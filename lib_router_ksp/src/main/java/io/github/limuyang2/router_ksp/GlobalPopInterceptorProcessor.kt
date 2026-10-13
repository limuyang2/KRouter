package io.github.limuyang2.router_ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate


/**
 * 全局拦截器处理器
 */
internal class GlobalPopInterceptorProcessor(
    val options: Map<String, String>,
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
) {
    fun process(resolver: Resolver): Pair<ModuleFileGlobalPopInterceptorInfo?, List<KSAnnotated>> {
        // 要查找的注解的全限定名
        val routerAnnotationName = "io.github.limuyang2.router.anno.RouterGlobalPopInterceptor"

        logger.info("RouteProcessor: start process: $routerAnnotationName")

        // 使用 resolver 查找所有被 @RouterGlobalPopInterceptor 注解标记的符号
        val symbols = resolver.getSymbolsWithAnnotation(routerAnnotationName, false)

        // 分离 Valid 和 Invalid
        val (valid, invalid) = symbols.partition { it.validate() }

        // 从有效符号中筛选出类声明
        val validSymbols = valid.filterIsInstance<KSClassDeclaration>()


        val annotationKSType =
            resolver.getClassDeclarationByName(resolver.getKSNameFromString(routerAnnotationName))
                ?.asType(emptyList())

        if(annotationKSType == null) {
            return null to invalid
        }

        // 如果找到了注解类型，则调用核心生成逻辑
        return generateCode(validSymbols, annotationKSType) to invalid
    }

    private fun generateCode(
        symbols: List<KSClassDeclaration>,
        annotationKSType: KSType
    ): ModuleFileGlobalPopInterceptorInfo? {
        val iterator = symbols.iterator()
        if (!iterator.hasNext()) {
            return null
        }

        val popList = mutableListOf<TempGlobalInterceptor>()

        // 生成 Provider 文件的包名
        var providerPackageName = ""
        // 最终生成 Provider 文件，所需要依赖的文件来源，用于增量编译
        val providerDependenciesKSFile = mutableSetOf<KSFile>()


        while (iterator.hasNext()) {
            val element: KSClassDeclaration = iterator.next()

            val targetAnnotation = element.annotations.find {
                if (it.shortName.asString() != "RouterGlobalPopInterceptor") {
                    false
                } else {
                    it.annotationType.resolve() == annotationKSType
                }
            }

            if (targetAnnotation == null) {
                continue
            }

            val elementQualifiedName = element.qualifiedName?.asString() ?: continue // 获取一次，如果为空则跳过
            val elementPackageName = element.packageName.asString()

            // ------------------- 处理 @RouterGlobalInterceptor 注解参数 --------------------------------------

            // 获取 注解的参数
            val priority = targetAnnotation.arguments.find {
                it.name?.asString() == "priority"
            }?.value as? Int ?: 10



            popList.add(
                TempGlobalInterceptor(
                    priority = priority,
                    clazzSimpleName = element.simpleName.asString(),
                    clazzPackageName = elementPackageName,
                    clazzQualifiedName = elementQualifiedName,
                    classKind = element.classKind
                )
            )

            // 记录依赖的文件
            element.containingFile?.let {
                providerDependenciesKSFile.add(it)
            }

            if (providerPackageName.isEmpty()) {
                // 取出class文件的包名，再截取前4个，生成新的包名
                providerPackageName =
                    elementPackageName.split(".").take(4).joinToString(".")
            }
        }

        // 按优先级排序
        popList.sortBy {
            it.priority
        }


        return ModuleFileGlobalPopInterceptorInfo(
            dependencies = providerDependenciesKSFile,
            packageName = providerPackageName,
            tempPopList = popList,
        )
    }
}
