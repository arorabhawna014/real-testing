package org.hrodberaht.injection.extensions.junit;

import org.hrodberaht.injection.InjectContainer;
import org.hrodberaht.injection.extensions.junit.internal.TransactionManager;
import org.hrodberaht.injection.extensions.junit.spi.PluginConfig;
import org.hrodberaht.injection.extensions.junit.spi.RunnerPlugins;
import org.hrodberaht.injection.internal.exception.InjectRuntimeException;
import org.hrodberaht.injection.spi.ContainerConfig;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;

/**
 * Unit Test JUnit (using @Inject)
 *
 * @author Robert Alexandersson
 *         2010-okt-11 19:32:34
 * @version 1.0
 * @since 1.0
 */
public class PluggableJUnitRunner extends BlockJUnit4ClassRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PluggableJUnitRunner.class);
    private InjectContainer activeContainer = null;
    private ContainerConfig creator = null;
    private RunnerPlugins runnerPlugins = null;

    /**
     * Creates a BlockJUnit4ClassRunner to run
     *
     * @throws InitializationError if the test class is malformed.
     */
    public PluggableJUnitRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
        createContainerFromRegistration();
    }

    private void createContainerFromRegistration() {
        try {
            Class testClass = getTestClass().getJavaClass();
            Annotation[] annotations = testClass.getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == ContainerContext.class) {
                    ContainerContext containerContext = (ContainerContext) annotation;
                    Class<? extends ContainerConfig> transactionClass = containerContext.value();
                    creator = transactionClass.newInstance();
                    runnerPlugins = getRunnerPlugins();
                    runnerPlugins.runInitBeforeContainer();
                    creator.createContainer();
                    runnerPlugins.runInitAfterContainer();

                    LOG.info("Creating creator for thread {}", Thread.currentThread().getName());
                }
            }
        } catch (InstantiationException | IllegalAccessException e) {
            throw new InjectRuntimeException(e);
        }
    }

    private RunnerPlugins getRunnerPlugins() {
        if(creator instanceof PluginConfig){
            return ((PluginConfig)creator).getRunnerPlugins();
        }else{
            return new RunnerPlugins();
        }
    }

    /**
     * @param frameworkMethod
     * @param notifier
     */
    @Override
    protected void runChild(FrameworkMethod frameworkMethod, RunNotifier notifier) {
        try {

            beforeRunChild();
            try {
                // This will execute the createTest method below, the activeContainer handling relies on this.
                LOG.info("START running test " +
                        frameworkMethod.getName() + " for thread " + Thread.currentThread().getName());
                super.runChild(frameworkMethod, notifier);
                LOG.info("END running test " +
                        frameworkMethod.getName() + " for thread " + Thread.currentThread().getName());
            } finally {
                afterRunChild();
            }
        } catch (Throwable e) {
            LOG.error("Fatal test error :"+frameworkMethod.getName(), e);
            Description description = describeChild(frameworkMethod);
            notifier.fireTestFailure(new Failure(description, e));
            notifier.fireTestFinished(description);
        }
    }

    protected void afterRunChild() {
        runnerPlugins.runAfterTest();
        TransactionManager.endTransaction();
        creator.cleanActiveContainer();
    }

    protected void beforeRunChild() {
        runnerPlugins.runBeforeTest();
        TransactionManager.beginTransaction(creator);

        // So that ContainerLifeCycleTestUtil can access the activeContainer and do magic
        creator.addSingletonActiveRegistry();

        activeContainer = creator.getActiveRegister().getContainer();
    }

    /**
     * Runs the injection of dependencies and resources on the test case before returned
     *
     * @return the testcase
     * @throws Exception
     */
    @Override
    protected Object createTest() throws Exception {
        Object testInstance = super.createTest();
        activeContainer.autowireAndPostConstruct(testInstance);
        return testInstance;
    }

}
