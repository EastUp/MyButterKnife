package com.east.butterknife;

import android.app.Activity;
import android.util.Log;

import java.lang.reflect.Constructor;

/**
 * description:
 * author: Darren on 2017/9/6 16:52
 * email: 240336124@qq.com
 * version: 1.0
 */
public class ButterKnife {
    public static Unbinder bind(Activity activity) {
        // xxxActivity_ViewBinding viewBinding = new xxxActivity_ViewBinding(this);
        try {
            Class<? extends Unbinder> bindClassName = (Class<? extends Unbinder>)
                    Class.forName(activity.getClass().getName() + "_ViewBinding");
            // 构造函数
            Constructor<? extends Unbinder> bindConstructor = bindClassName.getDeclaredConstructor(activity.getClass());
            //因为报名不同所以需要 强制访问
            bindConstructor.setAccessible(true);
            Unbinder unbinder = bindConstructor.newInstance(activity);
            // 返回 Unbinder
            return unbinder;
        } catch (Exception e) {
            Log.e("TAG",e.getMessage());
            e.printStackTrace();
        }

        return Unbinder.EMPTY;
    }
}
