/*
 * Copyright (c) 2017 org.hrodberaht
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

package org.hrodberaht.injection.plugin.junit.plugins;

import org.hrodberaht.injection.core.InjectContainer;
import org.hrodberaht.injection.core.spi.ResourceFactory;
import org.hrodberaht.injection.plugin.junit.api.annotation.RunnerPluginBeforeContainerCreation;
import org.hrodberaht.injection.plugin.junit.api.resource.ResourceProvider;
import org.hrodberaht.injection.plugin.junit.api.resource.ResourceProviderSupport;
import org.hrodberaht.injection.plugin.junit.datasource.DataSourceProxyInterface;
import org.hrodberaht.injection.plugin.junit.datasource.DatasourceResourceCreator;
import org.hrodberaht.injection.plugin.junit.api.Plugin;
import org.hrodberaht.injection.plugin.junit.api.PluginContext;
import org.hrodberaht.injection.plugin.junit.api.annotation.ResourcePluginFactory;
import org.hrodberaht.injection.plugin.junit.api.annotation.RunnerPluginAfterContainerCreation;
import org.hrodberaht.injection.plugin.junit.api.annotation.RunnerPluginAfterTest;
import org.hrodberaht.injection.plugin.junit.api.annotation.RunnerPluginBeforeTest;
import org.hrodberaht.injection.plugin.junit.datasource.DatasourceContainerService;
import org.hrodberaht.injection.plugin.junit.datasource.ProxyResourceCreator;
import org.hrodberaht.injection.plugin.junit.datasource.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DataSourcePlugin implements Plugin, ResourceProviderSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourcePlugin.class);
    private static final Map<Class, ResourceRunner> resourceRunnerState = new ConcurrentHashMap<>();

    private final List<ResourceRunner> beforeSuite = new ArrayList<>();

    private TransactionManager transactionManager;
    private InjectContainer injectContainer;

    private DatasourceResourceCreator datasourceResourceCreator;
    private ResourceContext resourceContext;
    private static ResourceContext sharedResourceContext;

    public DataSourcePlugin() {
        LOG.info("created {}", this);
    }

    @Override
    public Set<ResourceProvider> resources() {
        Set<ResourceProvider> resourceProviderSupports = new HashSet<>();
        getDataSources().forEach(
                dataSource -> resourceProviderSupports.add(
                        new ResourceProvider(dataSource.getName(), DataSource.class, () -> dataSource)
                )
        );
        return resourceProviderSupports;
    }


    private enum CommitMode {COMMIT, ROLLBACK}

    private CommitMode commitModeContainerLifeCycle = CommitMode.ROLLBACK;

    private boolean usingJavaContext = false;

    class ResourceContext {
        private final ResourceFactory resourceFactory;
        private final ProxyResourceCreator proxyResourceCreator;

        ResourceContext(ResourceFactory resourceFactory, ProxyResourceCreator proxyResourceCreator) {
            this.resourceFactory = resourceFactory;
            this.proxyResourceCreator = proxyResourceCreator;
        }
    }

    /**
     * This is useful if there is a need to run any code before the actual tests are executed, any results from code executed like this is commited to the underlying datasources and entitymanagers
     *
     * @param runnable the runnable to be added to run before tests start, comparable to @BeforeClass from JUnit, but with a but reusability over testsuites not onlt testclasses
     */
    public DataSourcePlugin addBeforeTestSuite(ResourceRunner runnable) {
        beforeSuite.add(runnable);
        return this;
    }

    public interface ResourceLoaderRunner {
        void run();
    }
    /**
     * Will store all resources in a shared mode to support java.context way of handling resources
     * @return same instance with changed value of usingContext
     */
    public DataSourcePlugin usingJavaContext(){
        this.usingJavaContext = true;
        return this;
    }

    public DataSourcePlugin commitAfterContainerCreation() {
        commitModeContainerLifeCycle = CommitMode.COMMIT;
        return this;
    }


    public DataSource createDataSource(){
        return getContextAwareResource().resourceFactory.getCreator(DataSource.class, usingJavaContext).create();
    }

    public DataSource createDataSource(String name){
        return getContextAwareResource().resourceFactory.getCreator(DataSource.class, usingJavaContext).create(name);
    }

    /**
     * Will load files from the path using a filename pattern matcher, will not look into child directories of the "classPathRoot"
     * First filter is a schema creator pattern as "create_schema_*.sql", example create_schema_user.sql
     * Second filter is a schema creator pattern as "create_schema_*.sql", example create_schema_user.sql
     * The order of the filters are guaranteed to follow create_schema first, insert_script second
     */
    public DataSourcePlugin loadSchema(DataSource dataSource, String classPathRoot) {
        DatasourceContainerService datasourceContainerService = new DatasourceContainerService(dataSource);
        datasourceContainerService.addSQLSchemas("main", classPathRoot);
        return this;
    }

    /**
     * Will load files from the path using a filename pattern matcher, will not look into child directories of the "classPathRoot"
     * First filter is a schema creator pattern as "create_schema_*.sql", example create_schema_user.sql
     * Second filter is a schema creator pattern as "insert_script*.sql", example insert_script_user.sql
     * The order of the filters are guaranteed to follow create_schema first, insert_script second
     */
    public DataSourcePlugin loadSchema(String schemaName, DataSource dataSource, String classPathRoot) {
        DatasourceContainerService datasourceContainerService = new DatasourceContainerService(dataSource);
        datasourceContainerService.addSQLSchemas(schemaName, classPathRoot);
        return this;
    }


    DatasourceResourceCreator getDatasourceResourceCreator(ProxyResourceCreator proxyResourceCreator) {
        return new DatasourceResourceCreator(proxyResourceCreator);
    }

    private ResourceContext getContextAwareResource() {
        if(this.usingJavaContext){
            if(sharedResourceContext == null){
                sharedResourceContext = new ResourceContext(this.resourceContext.resourceFactory, createContext());
            }
            if(datasourceResourceCreator == null){
                datasourceResourceCreator = getDatasourceResourceCreator(sharedResourceContext.proxyResourceCreator);
                sharedResourceContext.resourceFactory.addResourceCrator(datasourceResourceCreator);
            }
            return sharedResourceContext;
        }
        if(this.resourceContext.proxyResourceCreator == null){
            this.resourceContext = new ResourceContext(this.resourceContext.resourceFactory, createContext());
        }
        if(datasourceResourceCreator == null){
            datasourceResourceCreator = getDatasourceResourceCreator(resourceContext.proxyResourceCreator);
            resourceContext.resourceFactory.addResourceCrator(datasourceResourceCreator);
        }
        return this.resourceContext;
    }


    Collection<DataSourceProxyInterface> getDataSources(){
        return datasourceResourceCreator.getResources();
    }

    private ProxyResourceCreator createContext() {
        return new ProxyResourceCreator(
                ProxyResourceCreator.DataSourceProvider.HSQLDB,
                ProxyResourceCreator.DataSourcePersistence.RESTORABLE);
    }

    @ResourcePluginFactory
    private void setResourceFactory(ResourceFactory resourceFactory) {
        this.resourceContext = new ResourceContext(resourceFactory, null);
    }


    @RunnerPluginBeforeContainerCreation
    protected void beforeContainerCreation(PluginContext pluginContext) {
        LOG.info("beforeContainerCreation for {}", this);
        transactionManager = createTransactionManager();
        transactionManager.beginTransaction();
    }

    @RunnerPluginAfterContainerCreation
    protected void afterContainerCreation(PluginContext pluginContext) {
        this.injectContainer = pluginContext.register().getContainer();
        if (commitModeContainerLifeCycle == CommitMode.COMMIT) {
            transactionManager.endTransactionCommit();
        } else {
            transactionManager.endTransaction();
        }
        if (!beforeSuite.isEmpty()) {
            TransactionManager txM = createTransactionManager();
            txM.beginTransaction();
            beforeSuite.forEach(resourceRunner -> {
                ResourceLoader resourceLoader = new ResourceLoader(resourceRunner);
                resourceRunner.run(resourceLoader);
            });
            txM.endTransactionCommit();
        }
    }

    @RunnerPluginBeforeTest
    protected void beforeTest() {
        transactionManager = createTransactionManager();
        transactionManager.beginTransaction();
    }

    @RunnerPluginAfterTest
    protected void afterTest() {
        transactionManager.endTransaction();
    }

    @Override
    public LifeCycle getLifeCycle() {
        return LifeCycle.TEST_CONFIG;
    }


    public class ResourceLoader {
        private final ResourceRunner resourceRunner;

        ResourceLoader(ResourceRunner resourceRunner) {
            this.resourceRunner = resourceRunner;
        }

        public ResourceLoaderRunner get(Class<? extends ResourceLoaderRunner> aClass) {
            ResourceRunner resourceRunnerHolder = resourceRunnerState.get(aClass);
            if (resourceRunnerHolder == null) {
                resourceRunnerState.put(aClass, resourceRunner);
                return injectContainer.get(aClass);
            }
            return () -> {};
        }
    }

    TransactionManager createTransactionManager() {
        return new TransactionManager(getContextAwareResource().proxyResourceCreator);
    }


    public interface ResourceRunner {
        void run(ResourceLoader resourceLoader);
    }

}
