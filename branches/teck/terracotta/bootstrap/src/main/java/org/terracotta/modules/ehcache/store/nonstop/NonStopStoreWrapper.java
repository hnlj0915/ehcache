/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.modules.ehcache.store.nonstop;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.CacheConfiguration.TransactionalMode;
import net.sf.ehcache.config.NonstopConfiguration;
import net.sf.ehcache.config.TimeoutBehaviorConfiguration;
import net.sf.ehcache.search.Attribute;
import net.sf.ehcache.search.Results;
import net.sf.ehcache.search.SearchException;
import net.sf.ehcache.store.ElementValueComparator;
import net.sf.ehcache.store.Policy;
import net.sf.ehcache.store.StoreListener;
import net.sf.ehcache.store.StoreQuery;
import net.sf.ehcache.store.TerracottaStore;
import net.sf.ehcache.terracotta.TerracottaNotRunningException;
import net.sf.ehcache.writer.CacheWriterManager;

import org.terracotta.modules.ehcache.ToolkitInstanceFactory;
import org.terracotta.modules.ehcache.store.ToolkitNonStopExceptionOnTimeoutConfiguration;
import org.terracotta.toolkit.nonstop.NonStop;
import org.terracotta.toolkit.nonstop.NonStopException;
import org.terracotta.toolkit.rejoin.InvalidLockStateAfterRejoinException;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NonStopStoreWrapper implements TerracottaStore {
  private final TerracottaStore                               delegate;
  private final NonStop                                       nonStop;
  private final ToolkitNonStopExceptionOnTimeoutConfiguration toolkitNonStopConfiguration;
  private final NonstopConfiguration                          ehcacheNonStopConfiguration;
  private volatile TerracottaStore                            localReadDelegate;
  private final BulkOpsToolkitNonStopConfiguration            bulkOpsToolkitNonStopConfiguration;

  public NonStopStoreWrapper(TerracottaStore delegate, ToolkitInstanceFactory toolkitInstanceFactory,
                             NonstopConfiguration ehcacheNonStopConfiguration) {
    this.delegate = delegate;
    this.nonStop = toolkitInstanceFactory.getToolkit().getFeature(NonStop.class);
    this.ehcacheNonStopConfiguration = ehcacheNonStopConfiguration;
    this.toolkitNonStopConfiguration = new ToolkitNonStopExceptionOnTimeoutConfiguration(ehcacheNonStopConfiguration);
    this.bulkOpsToolkitNonStopConfiguration = new BulkOpsToolkitNonStopConfiguration(ehcacheNonStopConfiguration);
  }

  private TerracottaStore getTimeoutBehavior() {
    TimeoutBehaviorConfiguration behaviorConfiguration = ehcacheNonStopConfiguration.getTimeoutBehavior();
    switch (behaviorConfiguration.getTimeoutBehaviorType()) {
      case EXCEPTION:
        return ExceptionOnTimeoutStore.getInstance();
      case LOCAL_READS:
        if (localReadDelegate == null) {
          localReadDelegate = new LocalReadsOnTimeoutStore(delegate);
        }
        return localReadDelegate;
      case NOOP:
        return NoOpOnTimeoutStore.getInstance();
      default:
        return ExceptionOnTimeoutStore.getInstance();
    }
  }

  private static class BulkOpsToolkitNonStopConfiguration extends ToolkitNonStopExceptionOnTimeoutConfiguration {

    public BulkOpsToolkitNonStopConfiguration(NonstopConfiguration ehcacheNonStopConfig) {
      super(ehcacheNonStopConfig);
    }

    @Override
    public long getTimeoutMillis() {
      return ehcacheNonStopConfig.getBulkOpsTimeoutMultiplyFactor() * ehcacheNonStopConfig.getTimeoutMillis();
    }

  }

  private static void validateMethodNamesExist(Class klazz, Set<String> methodToCheck) {
    for (String methodName : methodToCheck) {
      if (!exist(klazz, methodName)) { throw new AssertionError("Method " + methodName + " does not exist in class "
                                                                + klazz.getName()); }
    }
  }

  private static boolean exist(Class klazz, String method) {
    Method[] methods = klazz.getMethods();
    for (Method m : methods) {
      if (m.getName().equals(method)) { return true; }
    }
    return false;
  }

  public static void main(String[] args) {
    PrintStream out = System.out;
    Class[] classes = { TerracottaStore.class };
    Set<String> bulkMethods = new HashSet<String>();
    bulkMethods.add("setNodeCoherent");
    bulkMethods.add("putAll");
    bulkMethods.add("getAllQuiet");
    bulkMethods.add("getAll");
    bulkMethods.add("removeAll");
    validateMethodNamesExist(TerracottaStore.class, bulkMethods);
    for (Class c : classes) {
      for (Method m : c.getMethods()) {
        out.println("/**");
        out.println("* {@inheritDoc}");
        out.println("*/");
        out.print("public " + m.getReturnType().getSimpleName() + " " + m.getName() + "(");
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
          out.print(params[i].getSimpleName() + " arg" + i);
          if (i < params.length - 1) {
            out.print(", ");
          }
        }
        out.print(")");

        Class<?>[] exceptions = m.getExceptionTypes();

        if (exceptions.length > 0) {
          out.print(" throws ");
        }
        for (int i = 0; i < exceptions.length; i++) {
          out.print(exceptions[i].getSimpleName());
          if (i < exceptions.length - 1) {
            out.print(", ");
          }
        }

        out.println(" {");
        out.println("    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!");
        if (bulkMethods.contains(m.getName())) {
          out.println("      nonStop.start(bulkOpsToolkitNonStopConfiguration);");
        } else {
          out.println("      nonStop.start(toolkitNonStopConfiguration);");
        }
        out.println("      try {");

        out.print("        ");
        if (m.getReturnType() != Void.TYPE) {
          out.print("return ");
        }
        out.print("this.delegate." + m.getName() + "(");
        for (int i = 0; i < params.length; i++) {
          out.print("arg" + i);
          if (i < params.length - 1) {
            out.print(", ");
          }
        }
        out.println(");");
        out.println("      } catch (NonStopException e) {");
        if (m.getReturnType() != Void.TYPE) {
          out.print("return ");
        }
        out.print("getTimeoutBehavior()." + m.getName() + "(");
        for (int i = 0; i < params.length; i++) {
          out.print("arg" + i);
          if (i < params.length - 1) {
            out.print(", ");
          }
        }
        out.println(");");

        out.println("      } catch (InvalidLockStateAfterRejoinException e) {");
        if (m.getReturnType() != Void.TYPE) {
          out.print("return ");
        }
        out.print("getTimeoutBehavior()." + m.getName() + "(");
        for (int i = 0; i < params.length; i++) {
          out.print("arg" + i);
          if (i < params.length - 1) {
            out.print(", ");
          }
        }
        out.println(");");
        out.println("      } finally {");
        out.println("        nonStop.finish();");
        out.println("      }");
        out.println("}");
        out.println("");
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Element unsafeGet(Object arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.unsafeGet(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().unsafeGet(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().unsafeGet(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set getLocalKeys() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getLocalKeys();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getLocalKeys();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getLocalKeys();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TransactionalMode getTransactionalMode() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getTransactionalMode();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getTransactionalMode();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getTransactionalMode();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Element get(Object arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.get(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().get(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().get(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean put(Element arg0) throws CacheException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.put(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().put(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().put(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Element replace(Element arg0) throws NullPointerException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.replace(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().replace(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().replace(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean replace(Element arg0, Element arg1, ElementValueComparator arg2) throws NullPointerException,
      IllegalArgumentException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.replace(arg0, arg1, arg2);
    } catch (NonStopException e) {
      return getTimeoutBehavior().replace(arg0, arg1, arg2);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().replace(arg0, arg1, arg2);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void putAll(Collection arg0) throws CacheException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(bulkOpsToolkitNonStopConfiguration);
    try {
      this.delegate.putAll(arg0);
    } catch (NonStopException e) {
      getTimeoutBehavior().putAll(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().putAll(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Element remove(Object arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.remove(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().remove(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().remove(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void flush() throws IOException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.flush();
    } catch (NonStopException e) {
      getTimeoutBehavior().flush();
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().flush();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean containsKey(Object arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.containsKey(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().containsKey(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().containsKey(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getSize() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getSize();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getSize();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getSize();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeAll(Collection arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(bulkOpsToolkitNonStopConfiguration);
    try {
      this.delegate.removeAll(arg0);
    } catch (NonStopException e) {
      getTimeoutBehavior().removeAll(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().removeAll(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeAll() throws CacheException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(bulkOpsToolkitNonStopConfiguration);
    try {
      this.delegate.removeAll();
    } catch (NonStopException e) {
      getTimeoutBehavior().removeAll();
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().removeAll();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Element removeElement(Element arg0, ElementValueComparator arg1) throws NullPointerException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.removeElement(arg0, arg1);
    } catch (NonStopException e) {
      return getTimeoutBehavior().removeElement(arg0, arg1);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().removeElement(arg0, arg1);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Element putIfAbsent(Element arg0) throws NullPointerException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.putIfAbsent(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().putIfAbsent(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().putIfAbsent(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setNodeCoherent(boolean arg0) throws UnsupportedOperationException, TerracottaNotRunningException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(bulkOpsToolkitNonStopConfiguration);
    try {
      this.delegate.setNodeCoherent(arg0);
    } catch (NonStopException e) {
      getTimeoutBehavior().setNodeCoherent(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().setNodeCoherent(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map getAllQuiet(Collection arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(bulkOpsToolkitNonStopConfiguration);
    try {
      return this.delegate.getAllQuiet(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().getAllQuiet(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getAllQuiet(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map getAll(Collection arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(bulkOpsToolkitNonStopConfiguration);
    try {
      return this.delegate.getAll(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().getAll(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getAll(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object getInternalContext() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getInternalContext();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getInternalContext();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getInternalContext();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasAbortedSizeOf() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.hasAbortedSizeOf();
    } catch (NonStopException e) {
      return getTimeoutBehavior().hasAbortedSizeOf();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().hasAbortedSizeOf();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getOnDiskSize() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getOnDiskSize();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getOnDiskSize();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getOnDiskSize();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean containsKeyOffHeap(Object arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.containsKeyOffHeap(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().containsKeyOffHeap(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().containsKeyOffHeap(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean containsKeyInMemory(Object arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.containsKeyInMemory(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().containsKeyInMemory(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().containsKeyInMemory(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setInMemoryEvictionPolicy(Policy arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.setInMemoryEvictionPolicy(arg0);
    } catch (NonStopException e) {
      getTimeoutBehavior().setInMemoryEvictionPolicy(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().setInMemoryEvictionPolicy(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Results executeQuery(StoreQuery arg0) throws SearchException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.executeQuery(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().executeQuery(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().executeQuery(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean putWithWriter(Element arg0, CacheWriterManager arg1) throws CacheException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.putWithWriter(arg0, arg1);
    } catch (NonStopException e) {
      return getTimeoutBehavior().putWithWriter(arg0, arg1);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().putWithWriter(arg0, arg1);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void recalculateSize(Object arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.recalculateSize(arg0);
    } catch (NonStopException e) {
      getTimeoutBehavior().recalculateSize(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().recalculateSize(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Element getQuiet(Object arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getQuiet(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().getQuiet(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getQuiet(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getInMemorySize() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getInMemorySize();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getInMemorySize();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getInMemorySize();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isCacheCoherent() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.isCacheCoherent();
    } catch (NonStopException e) {
      return getTimeoutBehavior().isCacheCoherent();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().isCacheCoherent();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getOffHeapSizeInBytes() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getOffHeapSizeInBytes();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getOffHeapSizeInBytes();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getOffHeapSizeInBytes();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object getMBean() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getMBean();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getMBean();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getMBean();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setPinned(Object arg0, boolean arg1) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.setPinned(arg0, arg1);
    } catch (NonStopException e) {
      getTimeoutBehavior().setPinned(arg0, arg1);
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().setPinned(arg0, arg1);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getOnDiskSizeInBytes() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getOnDiskSizeInBytes();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getOnDiskSizeInBytes();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getOnDiskSizeInBytes();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeStoreListener(StoreListener arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.removeStoreListener(arg0);
    } catch (NonStopException e) {
      getTimeoutBehavior().removeStoreListener(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().removeStoreListener(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getInMemorySizeInBytes() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getInMemorySizeInBytes();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getInMemorySizeInBytes();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getInMemorySizeInBytes();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isPinned(Object arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.isPinned(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().isPinned(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().isPinned(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getTerracottaClusteredSize() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getTerracottaClusteredSize();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getTerracottaClusteredSize();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getTerracottaClusteredSize();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void dispose() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.dispose();
    } catch (NonStopException e) {
      getTimeoutBehavior().dispose();
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().dispose();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void expireElements() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.expireElements();
    } catch (NonStopException e) {
      getTimeoutBehavior().expireElements();
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().expireElements();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean bufferFull() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.bufferFull();
    } catch (NonStopException e) {
      return getTimeoutBehavior().bufferFull();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().bufferFull();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isNodeCoherent() throws TerracottaNotRunningException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.isNodeCoherent();
    } catch (NonStopException e) {
      return getTimeoutBehavior().isNodeCoherent();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().isNodeCoherent();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addStoreListener(StoreListener arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.addStoreListener(arg0);
    } catch (NonStopException e) {
      getTimeoutBehavior().addStoreListener(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().addStoreListener(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isClusterCoherent() throws TerracottaNotRunningException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.isClusterCoherent();
    } catch (NonStopException e) {
      return getTimeoutBehavior().isClusterCoherent();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().isClusterCoherent();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void waitUntilClusterCoherent() throws UnsupportedOperationException, TerracottaNotRunningException,
      InterruptedException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.waitUntilClusterCoherent();
    } catch (NonStopException e) {
      getTimeoutBehavior().waitUntilClusterCoherent();
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().waitUntilClusterCoherent();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Policy getInMemoryEvictionPolicy() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getInMemoryEvictionPolicy();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getInMemoryEvictionPolicy();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getInMemoryEvictionPolicy();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Element removeWithWriter(Object arg0, CacheWriterManager arg1) throws CacheException {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.removeWithWriter(arg0, arg1);
    } catch (NonStopException e) {
      return getTimeoutBehavior().removeWithWriter(arg0, arg1);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().removeWithWriter(arg0, arg1);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List getKeys() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getKeys();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getKeys();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getKeys();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Status getStatus() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getStatus();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getStatus();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getStatus();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getOffHeapSize() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getOffHeapSize();
    } catch (NonStopException e) {
      return getTimeoutBehavior().getOffHeapSize();
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getOffHeapSize();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Attribute getSearchAttribute(String arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.getSearchAttribute(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().getSearchAttribute(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().getSearchAttribute(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean containsKeyOnDisk(Object arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      return this.delegate.containsKeyOnDisk(arg0);
    } catch (NonStopException e) {
      return getTimeoutBehavior().containsKeyOnDisk(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      return getTimeoutBehavior().containsKeyOnDisk(arg0);
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void unpinAll() {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.unpinAll();
    } catch (NonStopException e) {
      getTimeoutBehavior().unpinAll();
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().unpinAll();
    } finally {
      nonStop.finish();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAttributeExtractors(Map arg0) {
    // THIS IS GENERATED CODE -- DO NOT HAND MODIFY!
    nonStop.start(toolkitNonStopConfiguration);
    try {
      this.delegate.setAttributeExtractors(arg0);
    } catch (NonStopException e) {
      getTimeoutBehavior().setAttributeExtractors(arg0);
    } catch (InvalidLockStateAfterRejoinException e) {
      getTimeoutBehavior().setAttributeExtractors(arg0);
    } finally {
      nonStop.finish();
    }
  }

}
