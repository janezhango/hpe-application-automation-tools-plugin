/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2021 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.octane.testrunner;

import com.hp.octane.integrations.executor.TestsToRunConverterResult;
import com.hp.octane.integrations.executor.TestsToRunConvertersFactory;
import com.hp.octane.integrations.executor.TestsToRunFramework;
import com.hp.octane.integrations.utils.SdkStringUtils;
import com.microfocus.application.automation.tools.model.TestsFramework;
import com.microfocus.application.automation.tools.octane.configuration.ConfigurationValidator;
import com.microfocus.application.automation.tools.octane.executor.UftConstants;
import com.microfocus.application.automation.tools.octane.model.processors.projects.JobProcessorFactory;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for available frameworks for converting
 */
public class TestsToRunConverterBuilder extends Builder implements SimpleBuildStep {

    private TestsToRunConverterModel framework;

    public static final String TESTS_TO_RUN_PARAMETER = "testsToRun";

    private static final String DEFAULT_EXECUTING_DIRECTORY = "${workspace}";
    private static final String CHECKOUT_DIRECTORY_PARAMETER = "testsToRunCheckoutDirectory";

    public TestsToRunConverterBuilder(String framework) {
        this.framework = new TestsToRunConverterModel(framework, "");
    }

    @DataBoundConstructor
    public TestsToRunConverterBuilder(String framework, String format) {
        this.framework = new TestsToRunConverterModel(framework, format);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        try {
            ParametersAction parameterAction = build.getAction(ParametersAction.class);
            String rawTests = null;
            String executingDirectory = DEFAULT_EXECUTING_DIRECTORY;
            if (parameterAction != null) {
                ParameterValue suiteIdParameter = parameterAction.getParameter(UftConstants.SUITE_ID_PARAMETER_NAME);
                if (suiteIdParameter != null) {
                    printToConsole(listener, UftConstants.SUITE_ID_PARAMETER_NAME + " : " + suiteIdParameter.getValue());
                }
                ParameterValue suiteRunIdParameter = parameterAction.getParameter(UftConstants.SUITE_RUN_ID_PARAMETER_NAME);
                if (suiteRunIdParameter != null) {
                    printToConsole(listener, UftConstants.SUITE_RUN_ID_PARAMETER_NAME + " : " + suiteRunIdParameter.getValue());
                }

                ParameterValue executionIdParameter = parameterAction.getParameter(UftConstants.EXECUTION_ID_PARAMETER_NAME);
                if (executionIdParameter != null) {
                    printToConsole(listener, UftConstants.EXECUTION_ID_PARAMETER_NAME + " : " + executionIdParameter.getValue());
                }


                ParameterValue testsParameter = parameterAction.getParameter(TESTS_TO_RUN_PARAMETER);
                if (testsParameter != null && testsParameter.getValue() instanceof String) {
                    rawTests = (String) testsParameter.getValue();
                    printToConsole(listener, TESTS_TO_RUN_PARAMETER + " found with value : " + rawTests);
                }

                ParameterValue checkoutDirParameter = parameterAction.getParameter(CHECKOUT_DIRECTORY_PARAMETER);
                if (checkoutDirParameter != null) {
                    if (testsParameter.getValue() instanceof String && StringUtils.isNotEmpty((String) checkoutDirParameter.getValue())) {
                        executingDirectory = (String) checkoutDirParameter.getValue();//"%" + CHECKOUT_DIRECTORY_PARAMETER + "%";
                        printToConsole(listener, CHECKOUT_DIRECTORY_PARAMETER + " parameter found with value : " + executingDirectory);
                    } else {
                        printToConsole(listener, CHECKOUT_DIRECTORY_PARAMETER + " parameter found, but its value is empty or its type is not String. Using default value.");
                    }
                }
                printToConsole(listener, "checkout directory : " + executingDirectory);
            }
            if (StringUtils.isEmpty(rawTests)) {
                printToConsole(listener, TESTS_TO_RUN_PARAMETER + " is not found or has empty value. Skipping.");
                return;
            }

            if (framework == null || SdkStringUtils.isEmpty(getFramework())) {
                printToConsole(listener, "No frameworkModel is selected. Skipping.");
                return;
            }
            String frameworkName = getFramework();
            String frameworkFormat = getFormat();
            printToConsole(listener, "Selected framework = " + frameworkName);
            if (SdkStringUtils.isNotEmpty(frameworkFormat)) {
                printToConsole(listener, "Using format = " + frameworkFormat);
            }

            TestsToRunFramework testsToRunFramework = TestsToRunFramework.fromValue(frameworkName);
            TestsToRunConverterResult convertResult = TestsToRunConvertersFactory.createConverter(testsToRunFramework)
                    .setFormat(frameworkFormat)
                    .convert(rawTests, executingDirectory);
            printToConsole(listener, "Found #tests : " + convertResult.getTestsData().size());
            printToConsole(listener, "Set to parameter : " + convertResult.getTestsToRunConvertedParameterName() + " = " + convertResult.getConvertedTestsString());
            printToConsole(listener, "********************* Convertion is done *********************");
            if (JobProcessorFactory.WORKFLOW_RUN_NAME.equals(build.getClass().getName())) {
                List<ParameterValue> newParams = (parameterAction != null) ? new ArrayList<>(parameterAction.getAllParameters()) : new ArrayList<>();
                newParams.add(new StringParameterValue(convertResult.getTestsToRunConvertedParameterName(), convertResult.getConvertedTestsString()));
                ParametersAction newParametersAction = new ParametersAction(newParams);
                build.addOrReplaceAction(newParametersAction);
            } else {
                VariableInjectionAction via = new VariableInjectionAction(convertResult.getTestsToRunConvertedParameterName(), convertResult.getConvertedTestsString());
                build.addAction(via);
            }
        } catch (IllegalArgumentException e) {
            printToConsole(listener, "Failed to convert : " + e.getMessage());
            build.setResult(Result.FAILURE);

            return;
        }
    }

    /***
     * Used in UI
     * @return
     */
    public TestsToRunConverterModel getTestsToRunConverterModel() {
        return framework;
    }

    /***
     * Used in Pipeline
     * @return
     */
    public String getFramework() {
        return framework.getFramework().getName();
    }

    public String getFormat() {
        return framework.getFramework().getFormat();
    }

    public boolean getIsCustom() {
        return framework != null && TestsToRunFramework.Custom.value().equals(framework.getFramework().getName());
    }

    private void printToConsole(TaskListener listener, String msg) {
        listener.getLogger().println(this.getClass().getSimpleName() + " : " + msg);
    }

    @Symbol("convertTestsToRun")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;//FreeStyleProject.class.isAssignableFrom(jobType);
        }

        @Override
        public String getDisplayName() {
            return "ALM Octane testing framework converter";
        }

        public FormValidation doTestConvert(
                @QueryParameter("testsToRun") String rawTests,
                @QueryParameter("teststorunconverter.framework") String framework,
                @QueryParameter("teststorunconverter.format") String format) {

            try {

                if (StringUtils.isEmpty(rawTests)) {
                    throw new IllegalArgumentException("'Tests to run' parameter is missing");
                }

                if (StringUtils.isEmpty(framework)) {
                    throw new IllegalArgumentException("'Framework' parameter is missing");
                }

                TestsToRunFramework testsToRunFramework = TestsToRunFramework.fromValue(framework);
                if (TestsToRunFramework.Custom.equals(testsToRunFramework) && StringUtils.isEmpty(format)) {
                    throw new IllegalArgumentException("'Format' parameter is missing");
                }

                TestsToRunConverterResult convertResult = TestsToRunConvertersFactory.createConverter(testsToRunFramework)
                        .setFormat(format)
                        .convert(rawTests, TestsToRunConverterBuilder.DEFAULT_EXECUTING_DIRECTORY);
                String result = Util.escape(convertResult.getConvertedTestsString());
                return ConfigurationValidator.wrapWithFormValidation(true, "Conversion is successful : <div style=\"margin-top:20px\">" + result + "</div>");
            } catch (Exception e) {
                return ConfigurationValidator.wrapWithFormValidation(false, "Failed to convert : " + e.getMessage());
            }
        }

        /**
         * Gets report archive modes.
         *
         * @return the report archive modes
         */
        public List<TestsFramework> getFrameworks() {

            return TestsToRunConverterModel.Frameworks;
        }

    }
}
