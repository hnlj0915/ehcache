/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.modules.ehcache.txn.xa;

import net.sf.ehcache.transaction.XidTransactionIDSerializedForm;
import net.sf.ehcache.transaction.xa.XidTransactionID;

import javax.transaction.xa.Xid;

/**
 * @author Ludovic Orban
 */
public class ClusteredXidTransactionID implements XidTransactionID {

    private final Xid xid;
    private final String cacheName;
    private final String cacheManagerName;

    public ClusteredXidTransactionID(XidTransactionIDSerializedForm serializedForm) {
        this.xid = new XidClustered(serializedForm.getXid());
        this.cacheManagerName = serializedForm.getCacheManagerName();
        this.cacheName = serializedForm.getCacheName();
    }

    public ClusteredXidTransactionID(Xid xid, String cacheManagerName, String cacheName) {
        this.cacheManagerName = cacheManagerName;
        this.cacheName = cacheName;
        this.xid = new XidClustered(xid);
    }

    @Override
    public Xid getXid() {
        return xid;
    }

  @Override
  public String getCacheName() {
    return cacheName;
  }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof ClusteredXidTransactionID) {
            ClusteredXidTransactionID otherId = (ClusteredXidTransactionID) obj;
            return xid.equals(otherId.xid);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return xid.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Clustered [" + xid + "]";
    }

    private Object writeReplace() {
        return new XidTransactionIDSerializedForm(cacheManagerName, cacheName, xid);
    }
}
