package com.test.junit;

import com.test.junit.anotation.*;
import com.test.junit.assertion.AssertionsRuntimeException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;

public class TestRunner {

    private final List<Class<?>> testClasses = new ArrayList<>();

    public void register(Class<?> testClass) {
        testClasses.add(testClass);
    }

    public void register(Class<?>... testClasses) {
        this.testClasses.addAll(Arrays.asList(testClasses));
    }

    public void run() {
        testClasses.forEach(TestRunner::processTest);
    }

    private static void processTest(Class<?> test) {

        Object instance = createInstance(test);
        Method[] methods = test.getMethods();
        List<Method> testMethods = findMethodByAnnotation(methods, Test.class);
        List<Method> beforeEachMethods = findMethodByAnnotation(methods, BeforeMethod.class);
        List<Method> beforeAllMethods = findMethodByAnnotation(methods, BeforeAll.class);
        List<Method> afterEachMethods = findMethodByAnnotation(methods, AfterMethod.class);
        List<Method> afterAllMethods = findMethodByAnnotation(methods, AfterAll.class);

        invokeMethods(instance, beforeAllMethods);
        invokeTestMethods(instance, beforeEachMethods, testMethods, afterEachMethods);
        invokeMethods(instance, afterAllMethods);
    }

    private static Object createInstance(Class<?> test) {
        try {
            Constructor<?> constructor = test.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Method> findMethodByAnnotation(Method[] methods, Class<? extends Annotation> annotation) {
        return Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(annotation))
                .toList();
    }

    private static void invokeMethods(Object instance, List<Method> methods) {
        methods.forEach(method -> {
            try {
                method.invoke(instance);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void invokeTestMethods(Object instance, List<Method> beforeEachMethods,
                                          List<Method> methods, List<Method> afterEachMethods) {
        methods.forEach(method -> {
            try {
                method.setAccessible(true);
                invokeMethods(instance, beforeEachMethods);

                Timeout timeout = method.getAnnotation(Timeout.class);
                if (timeout != null) {
                    runWithTimeout(instance, method, afterEachMethods, timeout);
                } else {
                    method.invoke(instance);
                    invokeMethods(instance, afterEachMethods);
                    handleSunnyDayScenario(method);
                }
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof AssertionsRuntimeException) {
                    AssertionsRuntimeException ae = (AssertionsRuntimeException) e.getCause();
                    handleAssertionException(method, ae);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void runWithTimeout(Object instance, Method method, List<Method> afterEachMethods, Timeout timeout) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            try {
                method.invoke(instance);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });

        try {
            future.get(timeout.time(), timeout.timeUnit().toJavaTimeUnit());
            invokeMethods(instance, afterEachMethods);
            handleSunnyDayScenario(method);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AssertionsRuntimeException ae) {
                invokeMethods(instance, afterEachMethods);
                handleAssertionException(method, ae);
            }
        } catch (InterruptedException e) {
            invokeMethods(instance, afterEachMethods);
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            future.cancel(true);
            invokeMethods(instance, afterEachMethods);
            handleTimeoutScenario(method, timeout);
        } finally {
            executor.shutdown();
        }
    }


    private static void handleTimeoutScenario(Method method, Timeout timeout) {
        System.out.println(ConsoleColors.RED);
        System.out.println(String.format("[Test method %s] failed by timeout [%d %s]",
                getTestDescription(method), timeout.time(), timeout.timeUnit().name()));
        System.out.println(ConsoleColors.RESET);
    }

    private static String getTestDescription(Method method) {
        Description description = method.getAnnotation(Description.class);
        if (description != null && !description.message().isEmpty()) {
            return description.message();
        }
        return method.getName();
    }


    private static void handleAssertionException(Method method, AssertionsRuntimeException e) {
        System.out.println(ConsoleColors.RED);
        System.out.println(String.format("[Test method %s] is failed. Expected = [%s]; actual = [%s]", getTestDescription(method), e.getExpected(), e.getActual()));
        System.out.println(ConsoleColors.RESET);
    }

    private static void handleSunnyDayScenario(Method method) {
        System.out.println(ConsoleColors.GREEN);
        System.out.println(String.format("[Test method %s] is successful", getTestDescription(method)));
        System.out.println(ConsoleColors.RESET);
    }
}
