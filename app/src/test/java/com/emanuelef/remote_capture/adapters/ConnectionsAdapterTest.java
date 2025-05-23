/*
 * This file is part of PCAPdroid.
 *
 * PCAPdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PCAPdroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PCAPdroid.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2021 - Emanuele Faranda
 */

package com.emanuelef.remote_capture.adapters;

import android.content.Context;

import androidx.collection.ArraySet;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.emanuelef.remote_capture.AppsResolver;
import com.emanuelef.remote_capture.CaptureService;
import com.emanuelef.remote_capture.ConnectionsRegister;
import com.emanuelef.remote_capture.Whitebox;
import com.emanuelef.remote_capture.model.ConnectionDescriptor;
import com.emanuelef.remote_capture.model.ConnectionUpdate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
/* Tests the ConnectionsAdapter class by verifying its notifications sent when a connection is
 * added/removed/updated and the items retrieval via getItem/getItemCount methods.
 *
 * The conditions which characterize the longest code paths are:
 *  - With/without rollover: rollover occurs when the MAX_CONNECTIONS is exceeded so old connections
 *    are replaced with the ones.
 *  - Filtered/Unfiltered: the adapter handles differently the two cases when no connections filter
 *    is set and when one is set. In the first case, it relies on the ConnectionsRegister to retrieve
 *    the connections. In the latter case, it uses its own collection to store the connections matching
 *    the filter. Two types of filter can be applied: pre-defined filters via mFilter or substring
 *    matching via setSearch.
 *  - Stats/Info update: for efficiency reasons, updates are handled differently, via the
 *    setStats/setInfo methods.
 */
public class ConnectionsAdapterTest {
    static final int MAX_CONNECTIONS = 8;
    Context context;
    ConnectionsAdapter adapter;
    ConnectionsRegister reg;
    CaptureService service;
    int incrId = 0;

    /* This stores the notifications generated by the adapter when data changes. assertEvent and
     * getNotifiedPositions are then used to retrieve and test the events.
     */
    ArrayList<DataChangeEvent> pendingEvents = new ArrayList<>();

    enum ChangeType {
        ITEMS_INSERTED,
        ITEMS_UPDATED,
        ITEMS_REMOVED,
    }

    enum UpdateType {
        UPDATE_STATS,
        UPDATE_INFO
    }

    static class DataChangeEvent {
        public final ChangeType tp;
        public final int start;
        public final int count;

        public DataChangeEvent(ChangeType tp, int positionStart, int itemCount) {
            this.tp = tp;
            start = positionStart;
            count = itemCount;
        }
    }

    @Before
    public void setup() {
        incrId = 0;
        pendingEvents.clear();

        // NOTE: @BeforeClass (static) does not work with ApplicationProvider.getApplicationContext
        context = ApplicationProvider.getApplicationContext();
        AppsResolver resolver = new AppsResolver(context);
        adapter = new ConnectionsAdapter(context, resolver);

        // Register events observer
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                pendingEvents.add(new DataChangeEvent(ChangeType.ITEMS_INSERTED, positionStart, itemCount));
            }
            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                pendingEvents.add(new DataChangeEvent(ChangeType.ITEMS_UPDATED, positionStart, itemCount));
            }
            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                pendingEvents.add(new DataChangeEvent(ChangeType.ITEMS_REMOVED, positionStart, itemCount));
            }
        });

        // Max 8 connections
        reg = new ConnectionsRegister(context, MAX_CONNECTIONS);
        reg.addListener(adapter);

        // Mock CaptureService
        service = new CaptureService();
        Whitebox.setInternalState(service, "INSTANCE", service);
        Whitebox.setInternalState(service, "conn_reg", reg);
    }

    @After
    public void tearDown() {
        reg.removeListener(adapter);
        Whitebox.setInternalState(service, "INSTANCE", null);
    }

    @Test
    /* Simple insertion with no filter/rollover */
    public void testSimpleInsertion() {
        // start with 6 connections
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(false),
                newConnection(true),
                newConnection(true),
                newConnection(true),
                newConnection(true),
                newConnection(false),
        });
        assertEvent(ChangeType.ITEMS_INSERTED, 0, 6);
        assertEquals(0, adapter.getItem(0).incr_id);
        assertEquals(5, adapter.getItem(5).incr_id);
    }

    @Test
    /* Insertion with rollover but no filter */
    public void testInsertionRollover() {
        // start with 6 connections
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(false),
                newConnection(true),
                newConnection(true),
                newConnection(true),
                newConnection(true),
                newConnection(false),
        });
        assertEvent(ChangeType.ITEMS_INSERTED, 0, 6);

        // add 4 connections, 2 of which replace the first 2
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(false),
                newConnection(false),
                newConnection(true),
                newConnection(false),
        });

        assertEvent(ChangeType.ITEMS_REMOVED, 0, 2);
        assertEvent(ChangeType.ITEMS_INSERTED, 4, 4);
        assertEquals(2, adapter.getItem(0).incr_id);
        assertEquals(9, adapter.getItem(7).incr_id);
    }

    @Test
    /* Removal of all the connections via reset, no rollover/filter */
    public void testSimpleRemoveAll() {
        // start with 2 connections
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(true),
                newConnection(true),
        });

        // remove all items
        reg.reset();
        assertEquals(0, adapter.getItemCount());
        assertSame(adapter.getItem(0), null);
    }

    @Test
    /* Update of connections stats/info with no rollover and no filter */
    public void testUpdate() {
        // start with 2 connections
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(true),
                newConnection(true),
        });
        assertEvent(ChangeType.ITEMS_INSERTED, 0, 2);

        // update the connections
        reg.connectionsUpdates(new ConnectionUpdate[] {
                connUpdate(1, UpdateType.UPDATE_STATS),
        });
        assertEvent(ChangeType.ITEMS_UPDATED, 1, 1);

        reg.connectionsUpdates(new ConnectionUpdate[] {
                connUpdate(1, UpdateType.UPDATE_STATS),
                connUpdate(0, UpdateType.UPDATE_INFO),
                connUpdate(2, UpdateType.UPDATE_INFO), // untracked item, must be ignored
        });

        ArraySet<Integer> updated = getNotifiedPositions(ChangeType.ITEMS_UPDATED);
        assertEquals(2, updated.size());
        assertTrue(updated.contains(0));
        assertTrue(updated.contains(1));

        assertEquals(0, adapter.getItem(0).sent_pkts);
        assertNotSame(null, adapter.getItem(0).info);
        assertEquals(1, adapter.getItem(1).sent_pkts);
        assertSame(null, adapter.getItem(1).info);
    }

    @Test
    /* Insertion with rollover and status filter */
    public void testFilterRollover() {
        // start with 6 connections
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(false),
                newConnection(true),
                newConnection(true),
                newConnection(true), // pos 0 after remove
                newConnection(true),
                newConnection(false),
        });

        // apply filter: only active connections
        adapter.mFilter.status = ConnectionDescriptor.Status.STATUS_ACTIVE;
        adapter.refreshFilteredConnections();

        assertEquals(4, adapter.getItemCount());
        assertEquals(1, adapter.getItem(0).incr_id);
        assertEquals(4, adapter.getItem(3).incr_id);
        assertSame(null, adapter.getItem(4));
        pendingEvents.clear();

        // add 5 connections, 3 of which replace the first 3
        // this tests: removeFilteredItemAt
        reg.newConnections(new ConnectionDescriptor[]{
                newConnection(true),
                newConnection(false), // this one will be filtered out
                newConnection(true),
                newConnection(true),
                newConnection(true),
        });
        ArraySet<Integer> removed = getNotifiedPositions(ChangeType.ITEMS_REMOVED);
        assertEquals(2, removed.size());
        assertTrue(removed.contains(0));
        assertTrue(removed.contains(1));
        assertEvent(ChangeType.ITEMS_INSERTED, 2, 4);
        assertEquals(6, adapter.getItemCount());
        assertEquals(3, adapter.getItem(0).incr_id);
        assertEquals(10, adapter.getItem(5).incr_id);
        assertSame(null, adapter.getItem(6));

        // Add 3 active connections, which replace connections with ids 4,5,6 (last one filtered out)
        // tests connectionsAdded with non-0 mNumRemovedItems
        reg.newConnections(new ConnectionDescriptor[]{
                newConnection(true),
                newConnection(true),
        });
        removed = getNotifiedPositions(ChangeType.ITEMS_REMOVED);
        assertEquals(2, removed.size());
        assertEvent(ChangeType.ITEMS_INSERTED, 4, 2);
    }

    @Test
    /* Update of connections with rollover and status filter */
    public void testFilterUpdate() {
        adapter.mFilter.status = ConnectionDescriptor.Status.STATUS_ACTIVE;
        adapter.refreshFilteredConnections();

        // 8 connections (5 active connections) with 4 removed connections (mUnfilteredItemsCount not 0).
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(false), // true after remove
                newConnection(true),  // false after remove
                newConnection(true),  // false after remove
                newConnection(true),  // true after remove
                newConnection(true),  // pos 0 after remove
                newConnection(true),  // update: incr_id=5, pos=1
                newConnection(true),
                newConnection(false),
        });
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(true),
                newConnection(false),
                newConnection(false),
                newConnection(true),  // update: incr_id=11, pos=4
        });
        assertEquals(5, adapter.getItemCount());
        assertEquals(4, adapter.getItem(0).incr_id);
        assertEquals(11, adapter.getItem(4).incr_id);
        pendingEvents.clear();

        // update the connections
        // tests fixFilteredPositions
        reg.connectionsUpdates(new ConnectionUpdate[] {
                connUpdate(0, UpdateType.UPDATE_STATS),  // untracked (ignored)
                connUpdate(5, UpdateType.UPDATE_STATS),  // pos 1
                connUpdate(7, UpdateType.UPDATE_STATS),  // filtered out
                connUpdate(11, UpdateType.UPDATE_STATS), // pos 4
        });
        ArraySet<Integer> updated = getNotifiedPositions(ChangeType.ITEMS_UPDATED);
        assertEquals(2, updated.size());
        assertTrue(updated.contains(1));
        assertTrue(updated.contains(4));
        assertEquals(adapter.getItem(1).sent_pkts, 1);
        assertEquals(adapter.getItem(4).sent_pkts, 1);
        assertEquals(adapter.getItem(2).sent_pkts, 0);
    }

    @Test
    /* Test case for unmatched items.
     * When a filter is set, some connections which initially match the filter may not match it
     * anymore afterwards. This occurs, for example, with the "active" connection filter when a
     * connection transits to the "closed" state.
     */
    public void testFilterUnmatch() {
        adapter.mFilter.status = ConnectionDescriptor.Status.STATUS_ACTIVE;
        adapter.refreshFilteredConnections();

        // 8 connections (4 active connections) with 1 removed connections (mUnfilteredItemsCount not 0).
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(true),  // false after remove
                newConnection(false),
                newConnection(false),
                newConnection(true),  // pos 0 after remove, incr_id=3
                newConnection(true),  // unmatch pos 1, incr_id=4
                newConnection(false),
                newConnection(true),
                newConnection(true),  // unmatch pos3, incr_id=7
        });
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(false),
        });
        assertEquals(4, adapter.getItemCount());
        assertEquals(3, adapter.getItem(0).incr_id);
        pendingEvents.clear();

        // generate 2 unmatches
        ConnectionUpdate up1 = connUpdate(4, UpdateType.UPDATE_STATS);
        ConnectionUpdate up3 = connUpdate(7, UpdateType.UPDATE_STATS);
        up1.status = ConnectionDescriptor.CONN_STATUS_CLOSED;
        up3.status = ConnectionDescriptor.CONN_STATUS_CLOSED;

        // NOTE: the positions of the updates are sorted by the adapter. Reporting them here sorted
        // for the reader convenience.
        reg.connectionsUpdates(new ConnectionUpdate[] {
                up1,
                connUpdate(6, UpdateType.UPDATE_INFO), // pos 2
                up3,
        });
        assertEvent(ChangeType.ITEMS_REMOVED, 1, 1);
        assertEvent(ChangeType.ITEMS_UPDATED, 1, 1);
        assertNotSame(adapter.getItem(1).info, null);
        assertEvent(ChangeType.ITEMS_REMOVED, 2, 1);

        assertEquals(2, adapter.getItemCount());
        assertEquals(3, adapter.getItem(0).incr_id);
        assertEquals(6, adapter.getItem(1).incr_id);
    }

    @Test
    /* Insertion and updates with rollover and search string. */
    public void testSearch() {
        // 8 connections with 2 removed connections (mUnfilteredItemsCount not 0).
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(false),
                newConnection(true),
                newConnection(true),
                newConnection(true),
                newConnection(true),
                newConnection(true),
                newConnection(true),
                newConnection(false),
        });
        reg.newConnections(new ConnectionDescriptor[] {
                newConnection(false), // id 8
                newConnection(true),  // id 9
        });
        reg.connectionsUpdates(new ConnectionUpdate[] {
                connInfo(3, "orange"),
                connInfo(5, "juice"),
                connInfo(6, "apple"),
                connInfo(9, "orangejuice"),
        });

        // Set filter
        adapter.setSearch("orange");
        assertEquals(2, adapter.getItemCount());
        assertEquals(3, adapter.getItem(0).incr_id);
        assertEquals(9, adapter.getItem(1).incr_id);

        // Unmatch by changing the info
        reg.connectionsUpdates(new ConnectionUpdate[]{
                connInfo(3, "lemon"),
        });
        assertEquals(1, adapter.getItemCount());
        assertEquals(9, adapter.getItem(0).incr_id);

        // Unset filter
        adapter.setSearch(null);
        assertEquals(8, adapter.getItemCount());
        assertEquals(2, adapter.getItem(0).incr_id);
    }

    /* ******************************************************* */

    /* Creates a new ConnectionDescriptor and allocates an incrId for it. */
    ConnectionDescriptor newConnection(boolean active) {
        ConnectionDescriptor conn = new ConnectionDescriptor(incrId++, 4, 6,
                "1.1.1.1", "2.2.2.2", "", 51234, 80,
                0, -1, 0, false, 0);
        conn.status = active ? ConnectionDescriptor.CONN_STATUS_CONNECTED : ConnectionDescriptor.CONN_STATUS_CLOSED;
        return conn;
    }

    /* Creates a ConnectionUpdate based on the UpdateType:
     *   - UPDATE_STATS: sets the sent/rcvd pkts to 1, sent/rcvd bytes to 10, status to CONN_STATUS_CONNECTED
     *   - UPDATE_INFO: sets the info to "example.org" and L7 protocol to "TLS"
     */
    ConnectionUpdate connUpdate(int incr_id, UpdateType tp) {
        ConnectionUpdate update = new ConnectionUpdate(incr_id);

        if(tp.equals(UpdateType.UPDATE_STATS))
            update.setStats(0, 0, 10, 10, 1, 1,
                    0, 0, ConnectionDescriptor.CONN_STATUS_CONNECTED);
        else
            update.setInfo("example.org", null, "TLS", ConnectionUpdate.UPDATE_INFO_FLAG_ENCRYPTED_L7);

        return update;
    }

    /* Creates a ConnectionUpdate to set the connection info to the specified string.
     * The connection protocol is set to "TLS".
     */
    ConnectionUpdate connInfo(int incr_id, String info) {
        ConnectionUpdate update = new ConnectionUpdate(incr_id);
        update.setInfo(info, null, "TLS", ConnectionUpdate.UPDATE_INFO_FLAG_ENCRYPTED_L7);

        return update;
    }

    /* Retrieve the oldest event in pendingEvents and asserts it is of the specified type and
     * contains the specified range. */
    void assertEvent(ChangeType tp, int positionStart, int itemCount) {
        assertNotEquals(pendingEvents.size(), 0);

        DataChangeEvent ev = pendingEvents.remove(0);
        assertEquals(tp, ev.tp);
        assertEquals(positionStart, ev.start);
        assertEquals(itemCount, ev.count);
    }

    /* Retrieve the oldest consecutive events of the specified type.
     * Some notifications are sent as single events rather than in bulk. For example, when multiple
     * connections are updated, they are currently notified via notifyItemChanged rather than
     * notifyItemRangeChanged even for consecutive connections. By grouping events by type we can
     * ignore the details and provide more robust assertions.
     */
    ArraySet<Integer> getNotifiedPositions(ChangeType tp) {
        ArraySet<Integer> notified = new ArraySet<>();
        boolean[] removed_pos = new boolean[MAX_CONNECTIONS];

        while(!pendingEvents.isEmpty()) {
            DataChangeEvent ev = pendingEvents.get(0);
            if(!ev.tp.equals(tp))
                break;
            pendingEvents.remove(0);

            if(tp.equals(ChangeType.ITEMS_REMOVED)) {
                // Removed notification must be handled carefully as positions of preceding items
                // must be shifted when an item is removed in a previous notification, e.g.
                // rem(0) + rem(0) actually means remove item 0 and 1 in the original array
                boolean[] cur_removed = Arrays.copyOf(removed_pos, removed_pos.length);

                for(int i=0; i<ev.count; i++) {
                    int k = 0;
                    int found = -1;

                    // Skip previously removed items
                    for(int j=0; j<cur_removed.length; j++) {
                        if(cur_removed[j])
                            continue;

                        if(k == (ev.start + i)) {
                            found = j;
                            break;
                        }
                        k++;
                    }
                    assertNotEquals(-1, found);
                    assertFalse(removed_pos[found]);   // the item must not be already deleted
                    notified.add(found);
                    removed_pos[found] = true;
                }
            } else {
                for(int i = 0; i < ev.count; i++)
                    notified.add(ev.start + i);
            }
        }

        return notified;
    }
}
