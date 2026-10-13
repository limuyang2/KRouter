package io.github.limuyang2.router_ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.validate
import io.github.limuyang2.router_ksp.template.ConstructorInjectedProperty
import io.github.limuyang2.router_ksp.template.ConstructorParamInfo
import io.github.limuyang2.router_ksp.template.VarInjectedProperty
import io.github.limuyang2.router_ksp.template.createRouterInstanceTemplate
import org.apache.http.client.utils.URIBuilder

/**
 * 处理 @Router 的处理器
 */
internal class RouteProcessor(
    val options: Map<String, String>,
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
) {
    fun process(resolver: Resolver): Pair<ModuleRouterProviderFileRouterInfo?, List<KSAnnotated>> {
        // 要查找的注解的全限定名
        val routerAnnotationName = "io.github.limuyang2.router.anno.Router"

        logger.info("RouteProcessor: start process: $routerAnnotationName")

        // 使用 resolver 查找所有被 @Router 注解标记的符号（类、函数等）
        val symbols = resolver.getSymbolsWithAnnotation(routerAnnotationName, false)

        // 分离 Valid 和 Invalid
        val invalidSymbols = mutableListOf<KSAnnotated>() // 这些需要返回给 KSP 用于下一轮
        val validSymbols = mutableListOf<KSClassDeclaration>()

        symbols.forEach {
            if (!it.validate()) {
                invalidSymbols.add(it)
            } else if (it is KSClassDeclaration) {
                validSymbols.add(it)
            }
        }

        val annotationKSType =
            resolver.getClassDeclarationByName(resolver.getKSNameFromString(routerAnnotationName))
                ?.asType(emptyList())

        if(annotationKSType == null) {
            return null to invalidSymbols
        }

        // 如果找到了注解类型，则调用生成逻辑
        return generateCode(validSymbols, annotationKSType) to invalidSymbols
    }

    private fun generateCode(
        symbols: List<KSClassDeclaration>,
        annotationKSType: KSType,
    ): ModuleRouterProviderFileRouterInfo? {
        val iterator = symbols.iterator()
        if (!iterator.hasNext()) {
            return null
        }

        val tempRouteInfoList = ArrayList<TempRouteInfo>()
        val tempEntryInfoList = ArrayList<TempEntryInfo>()

        // 生成 Provider 文件的包名
        var providerPackageName = ""
        // 最终生成 Provider 文件，所需要依赖的文件来源，用于增量编译
        val providerDependenciesKSFile = mutableSetOf<KSFile>()

        while (iterator.hasNext()) {
            val element: KSClassDeclaration = iterator.next()

            val annotations = element.annotations.filter {
                // 先用 shortName 过滤（几乎零成本），再对少量候选做 resolve()
                if (it.shortName.asString() != "Router") {
                    false
                } else {
                    it.annotationType.resolve() == annotationKSType
                }
            }

            if (!annotations.iterator().hasNext()) {
                continue
            }

            val elementQualifiedName = element.qualifiedName?.asString() ?: continue // 获取一次，如果为空则跳过
            val elementPackageName = element.packageName.asString()
            val superTypeResult = scanSuperTypes(element)

            // ------------------- 处理 @Router 注解参数 --------------------------------------
            annotations.forEach { routerAnnotation ->

                // 获取 @Router 注解的参数 host\path 和 desc
                var scheme = ""
                var host = ""
                var path = ""
                var desc = ""
                var mPushInterceptors: List<KSType> = emptyList()
                var mPushInterceptorPriorities: List<Int> = emptyList()

                var mPopInterceptors: List<KSType> = emptyList()
                var mPopInterceptorPriorities: List<Int> = emptyList()

                routerAnnotation.arguments.forEach { argument ->
                    when (argument.name?.asString()) {
                        "scheme" -> scheme = argument.value.toString()
                        "host" -> host = argument.value.toString()
                        "path" -> path = argument.value.toString()
                        "desc" -> desc = argument.value.toString()
                        "pushInterceptors" -> {
                            mPushInterceptors = argument.value as List<KSType>
                        }

                        "pushInterceptorPriorities" -> {
                            mPushInterceptorPriorities = argument.value as List<Int>
                        }
                        "popInterceptors" -> {
                            mPopInterceptors = argument.value as List<KSType>
                        }

                        "popInterceptorPriorities" -> {
                            mPopInterceptorPriorities = argument.value as List<Int>
                        }
                    }
                }

                logger.info("RouteProcessor: 找到 @Router 注解的类: $elementQualifiedName")

                // 处理拦截器
                var pushInterceptors = emptyList<TempPageInterceptor>()
                if (mPushInterceptors.isNotEmpty()) {
                    pushInterceptors = mPushInterceptors.mapIndexed { index, it ->
                        val decl = it.declaration as KSClassDeclaration
                        val name = decl.qualifiedName!!.asString()
                        TempPageInterceptor(
                            priority = mPushInterceptorPriorities.getOrElse(index) { 10 },
                            clazzQualifiedName = name,
                            classKind = decl.classKind
                        )
                    }.sortedBy { it.priority }
                }

                var popInterceptors = emptyList<TempPageInterceptor>()
                if (mPopInterceptors.isNotEmpty()) {
                    popInterceptors = mPopInterceptors.mapIndexed { index, it ->
                        val decl = it.declaration as KSClassDeclaration
                        val name = decl.qualifiedName!!.asString()
                        TempPageInterceptor(
                            priority = mPopInterceptorPriorities.getOrElse(index) { 10 },
                            clazzQualifiedName = name,
                            classKind = decl.classKind
                        )
                    }.sortedBy { it.priority }
                }

                // path 不为空，才有url路由
                if (!path.isBlank()) {

                    // 没有 host 的情况下，读取 gradle 中 ksp 的配置
                    if (host.isBlank()) {
                        val defHost = options["RouterHost"]
                        if (defHost.isNullOrBlank()) {
                            logger.error("RouteProcessor: @Router 注解的类 [${elementQualifiedName}] 没有设置host，同时 Gradle 中也没有设置默认host。请补充完整。")
                            return null
                        }
                        host = defHost
                    }

                    // ------------------- 处理 @RouterParam 注解 --------------------------------------
                    // 如果注解存在，则为生成辅助类，用于实例化类
                    val clazz = generateInstanceCode(element, codeGenerator)
                    val instanceProviderString = "$clazz()"
                    // ------------------- 处理 @RouterParam 注解 end ------------------------------------


                    // 构建 路由信息 对象
                    val temp = TempRouteInfo(
                        scheme = scheme,
                        host = host,
                        path = path,
                        desc = desc,
                        pushInterceptors = pushInterceptors,
                        popInterceptors = popInterceptors,
                        clazzSimpleName = element.simpleName.asString(),
                        clazzPackageName = elementPackageName,
                        clazzQualifiedName = elementQualifiedName,
                        instanceProviderString = instanceProviderString,
                    )

                    tempRouteInfoList.add(temp)
                }
            }
            // ------------------- 处理 @Router 注解参数 end ------------------------------------


            // ------------------- 处理自动绑定 Entry  --------------------------------------
            val isEntry = superTypeResult.isEntry
            // 如果实现了 IRouterEntryProvider 接口
            if (isEntry) {
                tempEntryInfoList.add(
                    TempEntryInfo(
                        clazzQualifiedName = elementQualifiedName,
                        classKind = element.classKind,
                    )
                )
            }

            // ------------------- 处理自动绑定 Entry  end ------------------------------------


            providerDependenciesKSFile.add(element.containingFile!!)



            if (providerPackageName.isEmpty()) {
                // 取出class文件的包名，再截取前4个，生成新的包名
                providerPackageName =
                    elementPackageName.split(".").take(4).joinToString(".")
            }

        }


        // 检测重复路由（host + path 相同）
        val routeKeys = mutableMapOf<String, String>()
        for (info in tempRouteInfoList) {
            val key = "${info.host}/${info.path}"
            val existing = routeKeys[key]
            if (existing != null) {
                logger.error("重复路由: ${info.clazzQualifiedName} 和 $existing 定义了相同的路由 [host=${info.host}, path=${info.path}]")
            } else {
                routeKeys[key] = info.clazzQualifiedName
            }
        }

        return ModuleRouterProviderFileRouterInfo(
            dependencies = providerDependenciesKSFile,
            packageName = providerPackageName,
            tempRouteInfoList = tempRouteInfoList,
            tempEntryInfoList = tempEntryInfoList,
        )


    }

    // 用于跟踪已生成的 Instance 文件，防止重复生成
    private val generatedInstanceProviders = mutableSetOf<String>()

    private fun scanSuperTypes(classDeclaration: KSClassDeclaration): SuperTypeScanResult {
        var isEntry = false

        classDeclaration.superTypes.forEach { typeRef ->
            val resolved = typeRef.resolve()
            val qualifiedName = resolved.declaration.qualifiedName?.asString()

            if (qualifiedName == "io.github.limuyang2.router.IRouterEntryProvider") {
                isEntry = true
            }
        }

        return SuperTypeScanResult(isEntry)
    }

    private data class SuperTypeScanResult(
        val isEntry: Boolean,
    )


    /**
     * 为单个路由目标类生成对应的实例化辅助类。
     * 这个类负责从 Bundle 中拿取参数并创建类的实例。
     */
    private fun generateInstanceCode(
        classDeclaration: KSClassDeclaration,
        codeGenerator: CodeGenerator,
    ): String {
        val targetSimpleName = classDeclaration.simpleName.asString()

        val packageName = classDeclaration.packageName.asString()
        val instanceClassName = "${targetSimpleName}_Instance"
        val targetQualifiedName = classDeclaration.qualifiedName!!.asString()

        // 检查这个类是否已经处理过，防止重复生成而导致错误
        if (generatedInstanceProviders.contains(targetQualifiedName)) {
            logger.info("RouteProcessor: [$instanceClassName.kt] 在当前轮次已生成，跳过。")
            // 如果已经生成过，直接返回之前生成的完整类名
            return "$packageName.$instanceClassName"
        }


        // 获取主构造函数，如果不存在则无法处理
        val primaryConstructor = classDeclaration.primaryConstructor ?: run {
            logger.error("RouteProcessor: [$targetSimpleName] 没有主构造函数，无法生成 Instance。")
            throw RuntimeException("RouteProcessor: [$targetSimpleName] 没有主构造函数，无法生成 Instance。")
        }

        // 分析主构造函数的每一个参数
        val primaryConstructorParamsInfos =
            getConstructorParamInfos(primaryConstructor.parameters)

        var useConstructorParamsInfos = primaryConstructorParamsInfos

        // 如果主构造函数中没有被 @RouterParam 注解的参数，则尝试查找次构造函数中是否有被 @RouterParam 注解的参数
        if (primaryConstructorParamsInfos.all { it.injectedProperty == null }) {
            // 获取次构造函数
            val secondaryConstructors = classDeclaration.declarations
                .filterIsInstance<KSFunctionDeclaration>()
                .filter {
                    // 必须是构造函数
                    it.isConstructor() &&
                            // 并且不能是主构造函数
                            it != classDeclaration.primaryConstructor
                }

            // 在所有次构造函数中，找到那个带有 @RouterParam 注解的
            val constructorToUse = secondaryConstructors.firstOrNull { ctor ->
                // 检查构造函数的参数是否含有 @RouterParam 注解
                ctor.parameters.any { param ->
                    param.annotations.any { ann ->
                        ann.shortName.asString() == "RouterParam"
                    }
                }
            }

            if (constructorToUse != null) {
                // 找到了首个符合条件的次构造函数

                logger.info("RouteProcessor: [${classDeclaration.simpleName.asString()}] 次构造函数中有被 @RouterParam 注解的参数.")

                val infos = getConstructorParamInfos(constructorToUse.parameters)
                if (infos.any { it.injectedProperty != null }) {
                    // 有被 @RouterParam 注解的参数，则使用这个构造函数去创建
                    useConstructorParamsInfos = infos
                }
            } else {
                // 没有符合条件的次构造函数，就不用处理了，直接使用主构造函数去创建类了。
                logger.info("RouteProcessor: [${classDeclaration.simpleName.asString()}] 主构造函数和次构造函数都没有被 @RouterParam 注解的参数.")
            }
        }


        // 查找所有被 @RouterParam 注解的 并且是 var 的 内部属性
        /*
        例如：
        class A() {
            @RouterParam("x")
            var x : String = ""
        }
         */
        val mutableProperties = classDeclaration.getDeclaredProperties()
            .filter { prop -> prop.annotations.any { it.shortName.asString() == "RouterParam" } }
            // 单独找出被注解的 var 成员属性 (不在构造函数中的)
            .filter {
                it.isMutable && useConstructorParamsInfos.none { p -> p.name == it.simpleName.asString() }
            }
            .map { prop ->
                val annotation =
                    prop.annotations.first { it.shortName.asString() == "RouterParam" }
                val paramKey =
                    (annotation.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String)
                        ?.takeIf { it.isNotBlank() } ?: prop.simpleName.asString()

                val resolvedType = prop.type.resolve()

                val genericsTypeNames = resolvedType.arguments.map {
                    // 获取参数的泛型参数
                    it.type!!.resolve().declaration.qualifiedName!!.asString()
                }

                VarInjectedProperty(
                    paramKey = paramKey,
                    originalName = prop.simpleName.asString(),
                    typeName = resolvedType.declaration.qualifiedName!!.asString(),
                    genericsTypeNames = genericsTypeNames,
                    isMutable = prop.isMutable,
                    isNullable = resolvedType.isMarkedNullable
                )
            }


        // 创建文件...
        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, classDeclaration.containingFile!!),
            packageName = packageName,
            fileName = instanceClassName
        ).use { outputStream ->
            outputStream.appendText(
                createRouterInstanceTemplate(
                    packageName = packageName,
                    className = instanceClassName, // 类名
                    classKind = classDeclaration.classKind,
                    targetQualifiedName = targetQualifiedName,
                    constructorParamsInfos = useConstructorParamsInfos,
                    mutableProperties = mutableProperties.toList(),
                )
            )
        }

        logger.info("RouteProcessor: [$targetSimpleName] -> ${instanceClassName}.kt 生成完毕。")

        // 在成功生成后，将类名添加到集合中记录 ---
        generatedInstanceProviders.add(targetQualifiedName)

        // 返回完整的类名
        return "$packageName.$instanceClassName"
    }


    /**
     * 获取构造函数参数信息
     */
    private fun getConstructorParamInfos(parameters: List<KSValueParameter>): List<ConstructorParamInfo> {
        return parameters.map { param ->
            val paramName = param.name!!.asString()
            val resolvedType = param.type.resolve() // 解析类型

            // 检查是否被 @RouterParam 注解，并获取注解信息
            val varInjectedProperty = param.annotations
                .firstOrNull { it.shortName.asString() == "RouterParam" }
                ?.let { annotation ->
                    val paramKey =
                        (annotation.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String)
                            ?.takeIf { it.isNotBlank() } ?: paramName

                    ConstructorInjectedProperty(
                        paramKey = paramKey
                    )
                }

            val genericsTypeNames = resolvedType.arguments.mapNotNull {
                // 获取参数的泛型参数
                it.type?.resolve()?.declaration?.qualifiedName?.asString()
            }

            ConstructorParamInfo(
                name = paramName,
                typeName = resolvedType.declaration.qualifiedName!!.asString(),
                genericsTypeNames = genericsTypeNames,
                isNullable = resolvedType.isMarkedNullable,
                hasDefault = param.hasDefault,
                injectedProperty = varInjectedProperty // 被 @RouterParam 注解的信息，没有则为null
            )
        }
    }
}


internal data class TempEntryInfo(
    val clazzQualifiedName: String,
    val classKind: ClassKind,
)

internal data class TempRouteInfo(
    val scheme: String,
    /**
     * host
     */
    val host: String,
    /**
     * path
     */
    val path: String,
    /**
     * 描述
     */
    val desc: String,
    /**
     * 拦截器
     */
    val pushInterceptors: List<TempPageInterceptor>,
    val popInterceptors: List<TempPageInterceptor>,
    val clazzSimpleName: String,
    val clazzPackageName: String,
    val clazzQualifiedName: String,
    val instanceProviderString: String,
) {
    /**
     * 构建 URL 的代码表达式，在 TempRouteInfo 创建时一次性生成。
     * 例如: "https://example.com/path" 或 RouterSingleton.getConfig().defaultScheme + "://example.com/path"
     */
    val resolvedUrlExpression: String = if (host.isNotBlank()) {
        if (scheme.isNotBlank()) {
            "\"${URIBuilder().setScheme(scheme).setHost(host).setPath(path)}\""
        } else {
            "RouterSingleton.getConfig().defaultScheme + \":${URIBuilder().setHost(host).setPath(path)}\""
        }
    } else "\"\""
}

internal data class TempPageInterceptor(
    /**
     * 优先级, 值越小优先级越高
     */
    val priority: Int = 0,
    val clazzQualifiedName: String,
    val classKind: ClassKind,
)
