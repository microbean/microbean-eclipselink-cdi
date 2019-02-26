/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018–2019 microBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.eclipselink.cdi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import java.util.concurrent.Executor;

import javax.enterprise.inject.Instance;

import javax.enterprise.inject.spi.CDI;

import javax.enterprise.util.AnnotationLiteral;

import javax.inject.Qualifier;

import javax.management.MBeanServer;

import javax.transaction.TransactionManager;

import org.eclipse.persistence.platform.server.JMXServerPlatformBase;

import org.eclipse.persistence.sessions.DatabaseSession;
import org.eclipse.persistence.sessions.JNDIConnector;

import org.eclipse.persistence.transaction.JTATransactionController;

/**
 * A {@link JMXServerPlatformBase} that arranges things such that CDI,
 * not JNDI, will be used to acquire a {@link TransactionManager} and
 * {@link MBeanServer}.
 *
 * <p>Most users will not use this class directly, but will supply its
 * fully-qualified name as the value of the <a
 * href="https://www.eclipse.org/eclipselink/documentation/2.7/jpa/extensions/persistenceproperties_ref.htm#target-server">{@code
 * eclipselink.target-server} Eclipselink JPA extension property</a>
 * in a <a
 * href="https://javaee.github.io/tutorial/persistence-intro004.html#persistence-units">{@code
 * META-INF/persistence.xml} file</a>.</p>
 *
 * <p>For example:</p>
 *
 * <blockquote><pre>&lt;property name="eclipselink.target-server"
 *          value="org.microbean.eclipselink.cdi.CDISEPlatform"/&gt;</pre></blockquote>
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #getExternalTransactionControllerClass()
 */
public class CDISEPlatform extends JMXServerPlatformBase {


  /*
   * Instance fields.
   */
  

  private final Executor executor;

  private volatile Instance<MBeanServer> mBeanServerInstance;
  

  /*
   * Constructors.
   */

  
  /**
   * Creates a {@link CDISEPlatform}.
   *
   * @param session the {@link DatabaseSession} this platform will
   * wrap; must not be {@code null}
   *
   * @see JMXServerPlatformBase#JMXServerPlatformBase(DatabaseSession)
   */
  public CDISEPlatform(final DatabaseSession session) {
    super(session);
    final CDI<Object> cdi = CDI.current();
    assert cdi != null;
    if (cdi.select(TransactionManager.class).isUnsatisfied()) {
      this.disableJTA();
    }
    Instance<Executor> executorInstance = cdi.select(Executor.class, Eclipselink.Literal.INSTANCE);
    assert executorInstance != null;
    if (executorInstance.isUnsatisfied()) {
      executorInstance = cdi.select(Executor.class);
    }
    assert executorInstance != null;
    if (executorInstance.isUnsatisfied()) {
      this.executor = null;
    } else {
      this.executor = executorInstance.get();
    }
  }


  /*
   * Instance methods.
   */

  
  @Override
  public boolean isRuntimeServicesEnabledDefault() {
    Instance<MBeanServer> instance = this.mBeanServerInstance;
    final boolean returnValue;
    if (instance == null) {
      final CDI<Object> cdi = CDI.current();
      instance = cdi.select(MBeanServer.class, Eclipselink.Literal.INSTANCE);
      assert instance != null;
      if (instance.isUnsatisfied()) {
        instance = cdi.select(MBeanServer.class);
      }
      assert instance != null; 
      if (instance.isUnsatisfied()) {
        returnValue = false;
      } else {
        this.mBeanServerInstance = instance;
        returnValue = true;
      }
    } else {
      returnValue = !instance.isUnsatisfied();
    }
    return returnValue;
  }
  
  @Override
  public MBeanServer getMBeanServer() {
    if (this.mBeanServer == null) {
      final Instance<MBeanServer> instance = this.mBeanServerInstance;
      assert instance != null;
      if (!instance.isUnsatisfied()) {
        this.mBeanServer = instance.get();
      }
    }
    return super.getMBeanServer();
  }
  
  @Override
  public void launchContainerRunnable(final Runnable runnable) {
    if (runnable != null && this.executor != null) {
      this.executor.execute(runnable);
    } else {
      super.launchContainerRunnable(runnable);
    }
  }
  
  /**
   * Returns a non-{@code null} {@link Class} that extends {@link
   * org.eclipse.persistence.transaction.AbstractTransactionController}.
   *
   * @return a non-{@code null} {@link Class} that extends {@link
   * org.eclipse.persistence.transaction.AbstractTransactionController}
   *
   * @see org.eclipse.persistence.transaction.AbstractTransactionController
   */
  @Override
  public final Class<?> getExternalTransactionControllerClass() {
    if (this.externalTransactionControllerClass == null) {
      this.externalTransactionControllerClass = TransactionController.class;
    }
    return this.externalTransactionControllerClass;
  }

  /**
   * Returns {@link JNDIConnector#UNDEFINED_LOOKUP} when invoked.
   *
   * @return {@link JNDIConnector#UNDEFINED_LOOKUP}
   */
  @Override
  public final int getJNDIConnectorLookupType() {
    return JNDIConnector.UNDEFINED_LOOKUP;
  }

  
  /*
   * Inner and nested classes.
   */


  /**
   * A {@link JTATransactionController} whose {@link
   * #acquireTransactionManager()} uses CDI, not JNDI, to return a
   * {@link TransactionManager} instance.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   *
   * @see #acquireTransactionManager()
   *
   * @see JTATransactionController
   */
  public static final class TransactionController extends JTATransactionController {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link TransactionController}.
     */
    public TransactionController() {
      super();
    }


    /*
     * Instance methods.
     */
    

    /**
     * Returns a non-{@code null} {@link TransactionManager}.
     *
     * @return a non-{@code null} {@link TransactionManager}
     */
    @Override
    protected final TransactionManager acquireTransactionManager() {
      return CDI.current().select(TransactionManager.class).get();
    }

  }

  /**
   * A {@link Qualifier} used to designate various things as being
   * related to <a href="https://www.eclipse.org/eclipselink/"
   * target="_parent">Eclipselink</a> in some way.
   *
   * <p>The typical end user will apply this annotation to an
   * implementation of {@link Executor} if she wants that particular
   * {@link Executor} used by the {@link
   * CDISEPlatform#launchContainerRunnable(Runnable)} method.</p>
   *
   * <p>The {@link Eclipselink} qualifier may also be used to annotate
   * an implementation of {@link MBeanServer} for use by the {@link
   * CDISEPlatform#getMBeanServer()} method.</p>
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   *
   * @see CDISEPlatform#launchContainerRunnable(Runnable)
   *
   * @see CDISEPlatform#getMBeanServer()
   */
  @Documented
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ ElementType.FIELD, ElementType.METHOD, ElementType.TYPE })
  public @interface Eclipselink {    

    /**
     * An {@link AnnotationLiteral} that implements {@link
     * Eclipselink}.
     *
     * @author <a href="https://about.me/lairdnelson"
     * target="_parent">Laird Nelson</a>
     */
    public static final class Literal extends AnnotationLiteral<Eclipselink> implements Eclipselink {

      /**
       * The single instance of the {@link Literal} class.
       */
      public static final Eclipselink INSTANCE = new Literal();
      
      private static final long serialVersionUID = 1L;
      
    }
    
  }
  
}
