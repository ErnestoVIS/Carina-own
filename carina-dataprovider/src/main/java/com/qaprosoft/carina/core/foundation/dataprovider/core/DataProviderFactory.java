/*******************************************************************************
 * Copyright 2020-2022 Zebrunner Inc (https://www.zebrunner.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.qaprosoft.carina.core.foundation.dataprovider.core;

import com.qaprosoft.carina.core.foundation.dataprovider.core.groupping.GroupByImpl;
import com.qaprosoft.carina.core.foundation.dataprovider.core.groupping.GroupByMapper;
import com.qaprosoft.carina.core.foundation.dataprovider.core.groupping.exceptions.GroupByException;
import com.qaprosoft.carina.core.foundation.dataprovider.core.impl.BaseDataProvider;
import com.zebrunner.carina.utils.commons.SpecialKeywords;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Patotsky on 16.12.2014.
 */
public class DataProviderFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private DataProviderFactory() {}

    /**
     * Creates data provider from specified source in annotations.
     *
     * @param annotations test method annotations. For using carina's custom data provider
     *        test should be annotated with @CsvDataSourceParameters or @XlsDataSourceParameters
     *
     * @param context test context
     * @param m current test method
     * @return Object[][] provider from specified source,
     *         where provider.length shows number of test to be invoked
     *         and provider[i][] contains test invocation args for each i test
     */
    public static Object[][] getDataProvider(Annotation[] annotations, ITestContext context, ITestNGMethod m) {
        Map<String, String> tuidMap = Collections.synchronizedMap(new HashMap<>());
        Map<String, String> testNameMap = Collections.synchronizedMap(new HashMap<>());

        Object[][] provider = new Object[][]{};

        for (Annotation annotation : annotations) {
            String providerClass = findProviderClass(annotation);
            if (providerClass.isEmpty()) {
                continue;
            }

            Object providerObject = initDataProvider(providerClass);

            if (providerObject instanceof BaseDataProvider) {
                BaseDataProvider dataProvider = (BaseDataProvider) providerObject;
                provider = ArrayUtils.addAll(provider, dataProvider.getDataProvider(annotation, context, m));

                tuidMap.putAll(dataProvider.getTuidMap());
                testNameMap.putAll(dataProvider.getTestColumnNamesMap());
            }
        }

        if (!GroupByMapper.getInstanceInt().isEmpty() || !GroupByMapper.getInstanceStrings().isEmpty()) {
            provider = getGroupedList(provider);
        }
        putValuesToContext(context, tuidMap, testNameMap);

        // clear group by settings
        GroupByMapper.getInstanceInt().clear();
        GroupByMapper.getInstanceStrings().clear();

        return provider;
    }

    /**
     * Put data from test parameters to test's context. Necessary for correct test naming.
     *
     * @param context ITestContext.
     * @param tuidMap Map<String, String> contains tuid values for each test (if present)
     * @param testNameFromColumn Map<String, String> contains values for overriding test names from specified column
     */
    private static void putValuesToContext(ITestContext context,
                                           Map<String, String> tuidMap,
                                           Map<String, String> testNameFromColumn) {
        @SuppressWarnings("unchecked")
        Map<String, String> contextTUID = (Map<String, String>) context.getAttribute(SpecialKeywords.TUID);
        if (contextTUID != null) {
            contextTUID.putAll(tuidMap);
        } else {
            context.setAttribute(SpecialKeywords.TUID, tuidMap);
        }

        @SuppressWarnings("unchecked")
        Map<String, String> contextTestName = (Map<String, String>) context.getAttribute(SpecialKeywords.TEST_NAME);
        if (contextTestName != null) {
            contextTestName.putAll(testNameFromColumn);
        } else {
            context.setAttribute(SpecialKeywords.TEST_NAME, testNameFromColumn);
        }
    }

    /**
     * Finds class name for data provider implementation.
     *
     * @param annotation test method annotation
     * @return String class name of data provider if it was found in annotation classname() method. Empty if not
     */
    private static String findProviderClass(Annotation annotation) {
        Class<? extends Annotation> type = annotation.annotationType();
        String providerClass = "";

        try {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equalsIgnoreCase("classname")) {
                    providerClass = (String) method.invoke(annotation);
                    break;
                }
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failure on finding DataProvider class instance", e);
        }

        return providerClass;
    }

    /**
     * Initialize DataProvider based on className parameter.
     *
     * @param providerClass String full className
     * @return Object DataProvider Instance
     */
    private static Object initDataProvider(String providerClass) {
        Class<?> clazz;
        Object dataProvider = null;
        try {
            clazz = Class.forName(providerClass);
            Constructor<?> ctor = clazz.getConstructor();
            dataProvider = ctor.newInstance();
        } catch (Exception e) {
            LOGGER.error("DataProvider initialization failure", e);
        }

        return dataProvider;
    }

    private static Object[][] getGroupedList(Object[][] provider) {
        Object[][] finalProvider;
        if (GroupByMapper.isHashMapped()) {
            if (GroupByMapper.getInstanceStrings().size() == 1) {
                finalProvider = GroupByImpl.getGroupedDataProviderMap(provider, GroupByMapper.getInstanceStrings().iterator().next());
            } else {
                throw new GroupByException("Incorrect groupColumn annotation parameter!");
            }
        } else {
            if (GroupByMapper.getInstanceInt().size() == 1 && !GroupByMapper.getInstanceInt().contains(-1)) {

                finalProvider = GroupByImpl.getGroupedDataProviderArgs(provider, GroupByMapper.getInstanceInt().iterator().next());
            } else {
                throw new GroupByException("Incorrect groupColumn annotation  parameter!");
            }
        }

        return finalProvider;
    }

}
