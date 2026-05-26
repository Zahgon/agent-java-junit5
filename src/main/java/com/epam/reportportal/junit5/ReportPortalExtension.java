/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.junit5;

import com.epam.reportportal.annotations.*;
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.service.tree.TestItemTree;
import com.epam.reportportal.utils.AttributeParser;
import com.epam.reportportal.utils.IssueUtils;
import com.epam.reportportal.utils.ParameterUtils;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.reportportal.utils.formatting.MarkdownUtils;
import com.epam.ta.reportportal.ws.model.*;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.reactivex.Maybe;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.*;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import static com.epam.reportportal.junit5.ItemType.*;
import static com.epam.reportportal.junit5.SystemAttributesFetcher.collectSystemAttributes;
import static com.epam.reportportal.junit5.utils.ItemTreeUtils.createItemTreeKey;
import static com.epam.reportportal.listeners.ItemStatus.*;
import static com.epam.reportportal.service.tree.TestItemTree.createTestItemLeaf;
import static com.epam.reportportal.utils.formatting.ExceptionUtils.getStackTrace;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

/*
 * ReportPortal Extension sends the results of test execution to ReportPortal in RealTime
 */
public class ReportPortalExtension implements Extension, BeforeAllCallback, BeforeEachCallback, InvocationInterceptor, AfterTestExecutionCallback, AfterAllCallback, TestWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalExtension.class);

    private static final Set<String> ASSUMPTION_CLASSES = new HashSet<>(Arrays.asList(TestAbortedException.class.getCanonicalName(), "org.junit.AssumptionViolatedException"));

    private static final Predicate<Throwable> IS_ASSUMPTION = e -> ofNullable(e).map(Object::getClass).flatMap(c -> {
        Class<?> clazz = c;
        do {
            if (ASSUMPTION_CLASSES.contains(clazz.getCanonicalName())) {
                return of(clazz);
            }
        } while ((clazz = clazz.getSuperclass()) != null);
        return Optional.empty();
    }).isPresent();

    public static final TestItemTree TEST_ITEM_TREE = new TestItemTree();

    public static final ReportPortal REPORT_PORTAL = ReportPortal.builder().build();

    private static final Map<String, Launch> launchMap = new ConcurrentHashMap<>();

    private final Map<ExtensionContext, Maybe<String>> idMapping = new ConcurrentHashMap<>();

    private final Map<ExtensionContext, Maybe<String>> testTemplates = new ConcurrentHashMap<>();

    private final Map<ExtensionContext, List<ParameterResource>> testParameters = new ConcurrentHashMap<>();

    private final Set<ExtensionContext> failedClassInits = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static final String DESCRIPTION_TEST_ERROR_FORMAT = "Error: \n%s";

    @Nonnull
    protected Optional<Maybe<String>> getItemId(@Nonnull ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Finishes all launches for the JVM
     */
    @SuppressWarnings("unused")
    public void finish() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    private void finish(String id) {
        ofNullable(launchMap.remove(id)).ifPresent(ReportPortalExtension::finish);
    }

    private static void finish(Launch launch) {
        FinishExecutionRQ rq = new FinishExecutionRQ();
        rq.setEndTime(Instant.now());
        launch.finish(rq);
    }

    private static Thread getShutdownHook(final String launchId) {
        return new Thread(() -> ofNullable(launchMap.remove(launchId)).ifPresent(ReportPortalExtension::finish));
    }

    /**
     * Extension point to customize launch creation event/request
     *
     * @param parameters Launch Configuration parameters
     * @return Request to ReportPortal
     */
    protected StartLaunchRQ buildStartLaunchRq(ListenerParameters parameters) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * @return ReportPortal client instance
     */
    protected ReportPortal getReporter() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Returns a current launch unique ID
     *
     * @param context JUnit's launch context
     * @return ID of the launch
     */
    protected String getLaunchId(ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Returns a current launch instance, starts new if no such instance
     *
     * @param context JUnit's launch context
     * @return represents current launch
     */
    protected Launch getLaunch(ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Finish all test templates execution (basically a test class) within a specific context
     *
     * @param parentContext JUnit's test context
     */
    protected void finishTemplates(final ExtensionContext parentContext) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void afterAll(ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext parentContext) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation, ReflectiveInvocationContext<Constructor<T>> invocationContext, ExtensionContext parentContext) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void interceptAfterAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext parentContext) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public <T> T interceptTestFactoryMethod(Invocation<T> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Returns a status of a test based on execution exception
     *
     * @param context   JUnit's test context
     * @param throwable test exception
     * @return an {@link ItemStatus}
     */
    @SuppressWarnings("unused")
    @Nonnull
    protected ItemStatus getExecutionStatus(@Nonnull final ExtensionContext context, @Nullable final Throwable throwable) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Returns a status of a test based on whether or not it contains an execution exception
     *
     * @param context JUnit's test context
     * @return an {@link ItemStatus}
     */
    @Nonnull
    protected ItemStatus getExecutionStatus(@Nonnull final ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void interceptDynamicTest(Invocation<Void> invocation, DynamicTestInvocationContext invocationContext, ExtensionContext extensionContext) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Finishes a method marked with {@link BeforeAll} annotation, calls {@link ReportPortalExtension#reportSkippedClassTests} method in
     * case of failures
     *
     * @param invocation        the invocation that is being intercepted
     * @param invocationContext the context of the invocation that is being intercepted
     * @param context           JUnit's test context
     * @param id                an ID of the method to finish
     * @throws Throwable in case of failures
     */
    protected void finishBeforeAll(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context, Maybe<String> id) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Finishes a method marked with {@link BeforeEach} annotation, calls {@link ReportPortalExtension#reportSkippedStep} method in case of
     * failures
     *
     * @param invocation        the invocation that is being intercepted
     * @param invocationContext the context of the invocation that is being intercepted
     * @param context           JUnit's test context
     * @param id                an ID of the method to finish
     * @throws Throwable in case of failures
     */
    protected void finishBeforeEach(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context, Maybe<String> id) throws Throwable {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    private void finishBeforeAfter(Invocation<Void> invocation, ExtensionContext context, Maybe<String> id) throws Throwable {
        try {
            invocation.proceed();
        } catch (Throwable throwable) {
            finishBeforeAfter(context, id, getExecutionStatus(context, throwable));
            throw throwable;
        }
        finishBeforeAfter(context, id, PASSED);
    }

    private void finishBeforeAfter(ExtensionContext context, Maybe<String> id, ItemStatus status) {
        Launch launch = getLaunch(context);
        //noinspection ReactiveStreamsUnusedPublisher
        launch.finishTestItem(id, buildFinishTestItemRq(context, status));
    }

    /**
     * Starts a test template (basically a test class)
     *
     * @param parentContext JUnit's test context of a parent entity
     */
    protected void startTemplate(ExtensionContext parentContext) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Starts a test item of arbitrary type
     *
     * @param context JUnit's test context
     * @param type    a type of the item
     */
    protected void startTestItem(ExtensionContext context, ItemType type) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Starts a test item of arbitrary type
     *
     * @param context   JUnit's test context
     * @param arguments a list of test parameters
     * @param type      a type of the item
     */
    protected void startTestItem(ExtensionContext context, List<Object> arguments, ItemType type) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Starts a test item of arbitrary type
     *
     * @param context     JUnit's test context
     * @param arguments   a list of test parameters
     * @param itemType    a type of the item
     * @param description a description of the item
     * @param startTime   a start time of the item
     */
    protected void startTestItem(@Nonnull final ExtensionContext context, @Nonnull final List<Object> arguments, @Nonnull final ItemType itemType, @Nullable final String description, @Nullable final Instant startTime) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Starts the following methods: <code>@BeforeEach</code>, <code>@AfterEach</code>, <code>@BeforeAll</code> or <code>@AfterAll</code>
     *
     * @param method        a method reference
     * @param parentContext JUnit's test context of a parent item
     * @param context       JUnit's test context of a method to start
     * @param itemType      a method's item type (to display on RP)
     * @return an ID of the method
     */
    protected Maybe<String> startBeforeAfter(Method method, ExtensionContext parentContext, ExtensionContext context, ItemType itemType) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Finish a test template execution (basically a test class) with a specific status, builds a finish request based on the status
     *
     * @param context JUnit's test context
     */
    protected void finishTemplate(@Nonnull final ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Finishes a test item in RP, calculates the item status and builds a finish request based on the status
     *
     * @param context JUnit's test context
     */
    protected void finishTestItem(@Nonnull final ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Finishes a test item in RP with a specific status, builds a finish request based on the status
     *
     * @param context JUnit's test context
     * @param status  a test execution status
     */
    protected void finishTestItem(@Nonnull final ExtensionContext context, @Nullable final ItemStatus status) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Finishes a test in RP with a specific status, builds a finish request based on the status
     *
     * @param context JUnit's test context
     * @param status  a test execution status
     */
    protected void finishTest(@Nonnull final ExtensionContext context, @Nullable final ItemStatus status) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Finishes a test item in RP with a custom request
     *
     * @param context JUnit's test context
     * @param rq      a test item finish request
     */
    protected void finishTestItem(@Nonnull final ExtensionContext context, @Nonnull final FinishTestItemRQ rq) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Calculates a test case ID based on code reference and parameters
     *
     * @param method    a test method reference
     * @param codeRef   a code reference which will be used for the calculation
     * @param arguments a list of test arguments
     * @param instance  current test instance
     * @return a test case ID
     */
    protected TestCaseIdEntry getTestCaseId(@Nonnull final Method method, @Nonnull final String codeRef, @Nonnull final List<Object> arguments, @Nullable Object instance) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    private static String getCodeRef(@Nonnull final Method method) {
        return method.getDeclaringClass().getCanonicalName() + "." + method.getName();
    }

    private static String appendSuffixIfNotEmpty(final String str, @Nonnull final String suffix) {
        return str + (StringUtils.isNotBlank(suffix) ? "$" + suffix : "");
    }

    @Nonnull
    private String getCodeRef(@Nonnull final ExtensionContext context, @Nonnull final String currentCodeRef) {
        return context.getTestMethod().map(m -> appendSuffixIfNotEmpty(getCodeRef(m), currentCodeRef)).orElseGet(() -> context.getTestClass().map(c -> appendSuffixIfNotEmpty(c.getCanonicalName(), currentCodeRef)).orElseGet(() -> {
            String newCodeRef = appendSuffixIfNotEmpty(context.getDisplayName(), currentCodeRef);
            return context.getParent().map(c -> getCodeRef(c, newCodeRef)).orElse(newCodeRef);
        }));
    }

    /**
     * Returns a code reference of a test (static or dynamic). For dynamic tests appends each depth level where level names are display
     * names separated by `$` symbol
     *
     * @param context JUnit's test context
     * @return a code reference string
     */
    @Nonnull
    protected String getCodeRef(@Nonnull final ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Recursively returns the first real test method found in test hierarchy
     *
     * @param context JUnit's test context
     * @return an {@link Optional} of a {@link Method}
     */
    protected Optional<Method> getTestMethod(ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extract and returns static attributes of a test method or class (set with {@link Attributes} annotation)
     *
     * @param annotatedElement a test method or class reference
     * @return a set of attributes
     */
    @Nonnull
    protected Set<ItemAttributesRQ> getAttributes(@Nonnull final AnnotatedElement annotatedElement) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extracts and returns a test parameters, respects {@link ParameterKey} annotation
     *
     * @param method    a test method reference
     * @param arguments a list of parameter values
     * @return a list of parameters
     */
    @Nonnull
    protected List<ParameterResource> getParameters(@Nonnull final Method method, final List<Object> arguments) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    private Optional<Method> getOptionalTestMethod(ExtensionContext context) {
        Optional<Method> optionalMethod = context.getTestMethod();
        if (optionalMethod.isEmpty()) {
            //if not present means that we are in the dynamic test, in this case we need to move to the parent context
            Optional<ExtensionContext> parentContext = context.getParent();
            if (parentContext.isEmpty()) {
                return Optional.empty();
            }
            return parentContext.get().getTestMethod();
        }
        return optionalMethod;
    }

    private String getMethodName(String value) {
        return value.length() > 1024 ? value.substring(0, 1021) + "..." : value;
    }

    /**
     * Extension point to customize test step name
     *
     * @param context  JUnit's test context
     * @param itemType a test method item type
     * @return Test/Step Name being sent to ReportPortal
     */
    protected String createStepName(ExtensionContext context, ItemType itemType) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test step creation event/request
     *
     * @param context     JUnit's test context
     * @param arguments   a test arguments list
     * @param itemType    a test method item type
     * @param description a description of the item
     * @param startTime   a start time of the test
     * @return Request to ReportPortal
     */
    @Nonnull
    protected StartTestItemRQ buildStartStepRq(@Nonnull final ExtensionContext context, @Nonnull final List<Object> arguments, @Nonnull final ItemType itemType, @Nullable final String description, @Nullable final Instant startTime) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize beforeXXX creation event/request
     *
     * @param method        JUnit's test method reference
     * @param parentContext JUnit's context of a parent item
     * @param context       JUnit's test context
     * @param itemType      a type of the item to build
     * @return Request to ReportPortal
     */
    @Nonnull
    @SuppressWarnings("unused")
    protected StartTestItemRQ buildStartConfigurationRq(@Nonnull Method method, @Nonnull ExtensionContext parentContext, @Nonnull ExtensionContext context, @Nonnull ItemType itemType) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize skipped test insides
     *
     * @param context JUnit's test context
     * @param cause   an error thrown by skip culprit
     */
    @SuppressWarnings("unused")
    protected void createSkippedSteps(ExtensionContext context, Throwable cause) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize a test result on it's finish
     *
     * @param context JUnit's test context
     * @param status  a test item execution result
     * @return Request to ReportPortal
     */
    @Nonnull
    protected FinishTestItemRQ buildFinishTestRq(@Nonnull ExtensionContext context, @Nullable ItemStatus status) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Nullable
    protected com.epam.ta.reportportal.ws.model.issue.Issue getIssue(@Nonnull ExtensionContext context) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize a test item result on it's finish
     *
     * @param context JUnit's test context
     * @param status  a test item execution result
     * @return Request to ReportPortal
     */
    @SuppressWarnings("unused")
    @Nonnull
    protected FinishTestItemRQ buildFinishTestItemRq(@Nonnull ExtensionContext context, @Nullable ItemStatus status) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize beforeXXX step name
     *
     * @param testClass JUnit's test class, by which the name will be calculated
     * @param method    JUnit's test method reference
     * @return Test/Step Name being sent to ReportPortal
     */
    @Nonnull
    protected String createConfigurationName(@Nonnull Class<?> testClass, @Nonnull Method method) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test step description
     *
     * @param context  JUnit's test context
     * @param itemType a test method item type
     * @return Test/Step Description being sent to ReportPortal
     */
    @Nonnull
    protected String createStepDescription(ExtensionContext context, final ItemType itemType) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize beforeXXX step description
     *
     * @param testClass JUnit's test class, by which the name will be calculated
     * @param method    JUnit's test method reference
     * @return Test/Step Description being sent to ReportPortal
     */
    @SuppressWarnings("unused")
    protected String createConfigurationDescription(Class<?> testClass, Method method) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test steps skipped in case of a <code>@BeforeEach</code> method failed.
     *
     * @param invocationContext JUnit's <code>@BeforeAll</code> invocation context
     * @param context           JUnit's test context
     * @param throwable         An exception which caused the skip
     * @param eventTime         <code>@BeforeEach</code> start time
     */
    protected void reportSkippedStep(ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context, Throwable throwable, Instant eventTime) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test steps skipped in case of a <code>@BeforeAll</code> method failed.
     *
     * @param invocationContext JUnit's <code>@BeforeAll</code> invocation context
     * @param context           JUnit's test context
     * @param eventTime         <code>@BeforeAll</code> start time
     */
    @SuppressWarnings("unused")
    protected void reportSkippedClassTests(ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context, Instant eventTime) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }
}
