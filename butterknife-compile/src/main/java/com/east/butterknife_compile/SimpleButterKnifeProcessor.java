package com.east.butterknife_compile;

import com.east.butterknife_annotations.BindView;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * |---------------------------------------------------------------------------------------------------------------|
 *  @description:  注解处理类
 *  @author: East
 *  @date: 2020-02-12 18:29
 * |---------------------------------------------------------------------------------------------------------------|
 */

/**
 * AutoService  配置一个spi,在resources目录新建META-INF/services/javax.annotation.processing.Processor，文件里的内容为ButterKnifeProcessor类全名。
 */
// AutoService则是固定的写法，加个注解即可
// 通过auto-service中的@AutoService可以自动生成AutoService注解处理器，用来注册
// 用来生成 META-INF/services/javax.annotation.processing.Processor 文件
@AutoService(Processor.class)
public class SimpleButterKnifeProcessor extends AbstractProcessor {
    //档案/文件管理器
    private Filer mFiler;
    //用于对程序Element进行操作的实用方法。
    private Elements mElementUtils;
    //用于对Type进行操作的实用方法。
    private Types mTypeUtils;
    private Trees mTrees;
    private final Map<QualifiedId, Id> symbols = new LinkedHashMap<>();

    private static final List<String> SUPPORTED_TYPES = Arrays.asList(
            "array", "attr", "bool", "color", "dimen", "drawable", "id", "integer", "string"
    );
    private Messager mMessager;

    /**
     * 初始化处理器
     * @param processingEnv
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mFiler = processingEnv.getFiler(); //获取创建新文件需要的档案
        mElementUtils = processingEnv.getElementUtils();
        mTypeUtils = processingEnv.getTypeUtils();
        mMessager = processingEnv.getMessager();

        // 通过ProcessingEnvironment去获取build.gradle传过来的参数
        String content = processingEnv.getOptions().get("content");
        // 有坑：Diagnostic.Kind.ERROR，异常会自动结束，不像安卓中Log.e那么好使
        mMessager.printMessage(Diagnostic.Kind.NOTE,content);
        try {
            mTrees = Trees.instance(processingEnv);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @return 注解集合,指定可以被该注解器处理的注解类型集合
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        for (Class<? extends Annotation> annotation : getSupportedAnnotations()) {
            types.add(annotation.getCanonicalName());
        }
        return types;
    }

    /**
     *  参考butterknife的源码写法
     */
    private Set<Class<? extends Annotation>> getSupportedAnnotations(){
        Set<Class<? extends Annotation>> annotations = new LinkedHashSet<>();
        annotations.add(BindView.class);
        return annotations;
    }

    /**
     *  指定正在使用的java版本
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }

    /**
     *
     * @param annotations
     * @param roundEnvironment
     * @return  这些注解是否由 这个processor 处理.
     *          true 后续不会被其它处理器处理
     *          false 后续可以被其它处理器处理
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        //调试打印
        System.out.println("调试----------------------->");
        System.out.println("调试----------------------->1");


        // process 方法代表的是，有注解就都会进来 ，但是这里面是一团乱麻
        Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(BindView.class);
        /*for (Element element : elements) {

            Element enclosingElement = element.getEnclosingElement();
            System.out.println("------------------------>"+element.getSimpleName().toString()+" "+enclosingElement.getSimpleName().toString());
        }*/
        // 解析 属性 activity -> List<Element>
        Map<Element, List<Element>> elementsMap = new LinkedHashMap<>();
        for (Element element : elements) {
            Element enclosingElement = element.getEnclosingElement();

            List<Element> viewBindElements = elementsMap.get(enclosingElement);
            if (viewBindElements == null) {
                viewBindElements = new ArrayList<>();
                elementsMap.put(enclosingElement, viewBindElements);
            }

            viewBindElements.add(element);
        }

        // 生成代码
        for (Map.Entry<Element, List<Element>> entry : elementsMap.entrySet()) {
            Element enclosingElement = entry.getKey();
            List<Element> viewBindElements = entry.getValue();

            // public final class xxxActivity_ViewBinding implements Unbinder
            String activityClassNameStr = enclosingElement.getSimpleName().toString();
            ClassName activityClassName = ClassName.bestGuess(activityClassNameStr);
            ClassName unbinderClassName = ClassName.get("com.east.butterknife","Unbinder");
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(activityClassNameStr+"_ViewBinding")
                    .addModifiers(Modifier.FINAL,Modifier.PUBLIC).addSuperinterface(unbinderClassName)
                    .addField(activityClassName,"target",Modifier.PRIVATE);


            // 实现 unbind 方法
            // android.support.annotation.CallSuper
            ClassName callSuperClassName = ClassName.get("androidx.annotation","CallSuper");
            MethodSpec.Builder unbindMethodBuilder = MethodSpec.methodBuilder("unbind")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC,Modifier.FINAL)
                    .addAnnotation(callSuperClassName);

            unbindMethodBuilder.addStatement("$T target = this.target",activityClassName);
            unbindMethodBuilder.addStatement("if (target == null) throw new IllegalStateException(\"Bindings already cleared.\");");

            // 构造函数
            MethodSpec.Builder constructorMethodBuilder = MethodSpec.constructorBuilder()
                    .addParameter(activityClassName,"target");
            // this.target = target;
            constructorMethodBuilder.addStatement("this.target = target");
            // findViewById 属性
            for (Element viewBindElement : viewBindElements) {
                // target.textView1 = Utils.findRequiredViewAsType(source, R.id.tv1, "field 'textView1'", TextView.class);
                // target.textView1 = Utils.findViewById(source, R.id.tv1);
                String filedName = viewBindElement.getSimpleName().toString();
                ClassName utilsClassName = ClassName.get("com.east.butterknife","Utils");
                int resId = viewBindElement.getAnnotation(BindView.class).value();
                constructorMethodBuilder.addStatement("target.$L = $T.findViewById(target, $L)",filedName,utilsClassName,resId);
                // target.textView1 = null;
                unbindMethodBuilder.addStatement("target.$L = null",filedName);
            }


            classBuilder.addMethod(unbindMethodBuilder.build());
            classBuilder.addMethod(constructorMethodBuilder.build());

            // 生成类，看下效果
            try {
                String packageName = mElementUtils.getPackageOf(enclosingElement).getQualifiedName().toString();

                JavaFile.builder(packageName,classBuilder.build())
                        .addFileComment("butterknife 自动生成")
                        .build().writeTo(mFiler);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("翻车了！");
            }
        }

        return true;
    }

}
