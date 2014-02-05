/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 *//*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.safehaus.kiskis.mgmt.server.ui.modules.oozie;

//import org.safehaus.kiskis.mgmt.server.ui.modules.hbase.wizard.HBaseConfig;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.safehaus.kiskis.mgmt.shared.protocol.Agent;
import org.safehaus.kiskis.mgmt.shared.protocol.HadoopClusterInfo;
import org.safehaus.kiskis.mgmt.shared.protocol.ServiceLocator;
import org.safehaus.kiskis.mgmt.shared.protocol.Util;
import org.safehaus.kiskis.mgmt.shared.protocol.api.AgentManager;
import org.safehaus.kiskis.mgmt.shared.protocol.api.DbManager;

/**
 *
 * @author dilshat
 */
public class OozieDAO {

    private static final Logger LOG = Logger.getLogger(OozieDAO.class.getName());
    private static final DbManager dbManager;
    private static final AgentManager agentManager;

    static {
        dbManager = ServiceLocator.getService(DbManager.class);
        agentManager = ServiceLocator.getService(AgentManager.class);
    }

    public static boolean saveClusterInfo(OozieConfig cluster) {
        try {

            byte[] data = Util.serialize(cluster);

            String cql = "insert into hbase_info (uid, info) values (?,?)";
            dbManager.executeUpdate(cql, cluster.getUuid(), ByteBuffer.wrap(data));

        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error in saveHBaseClusterInfo", ex);
            return false;
        }
        return true;
    }

    public static List<OozieConfig> getClusterInfo() {
        List<OozieConfig> list = new ArrayList<OozieConfig>();
        try {
            String cql = "select * from hbase_info";
            ResultSet results = dbManager.executeQuery(cql);
            for (Row row : results) {

                ByteBuffer data = row.getBytes("info");

                byte[] result = new byte[data.remaining()];
                data.get(result);
                OozieConfig config = (OozieConfig) deserialize(result);
                list.add(config);
            }
        } catch (ClassNotFoundException ex) {
            LOG.log(Level.SEVERE, "Error in getHBaseClusterInfo", ex);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error in getHBaseClusterInfo", ex);
        }
        return list;
    }

    public static boolean deleteClusterInfo(UUID uuid) {
        try {
            String cql = "delete from hbase_info where uid = ?";
            dbManager.executeUpdate(cql, uuid);
            return true;
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Error in deleteHBaseClusterInfo(name)", ex);
        }
        return false;
    }

    public static Object deserialize(byte[] bytes) throws ClassNotFoundException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public static Set<Agent> getAgents(Set<UUID> uuids) {
        Set<Agent> list = new HashSet<Agent>();
        for (UUID uuid : uuids) {
            Agent agent = agentManager.getAgentByUUIDFromDB(uuid);
            list.add(agent);
        }
        return list;
    }

    public static HadoopClusterInfo getHadoopClusterInfo(UUID uuid) {
        HadoopClusterInfo hadoopClusterInfo = null;
        try {
            String cql = "select * from hadoop_cluster_info where uid = ? limit 1 allow filtering";
            ResultSet rs = dbManager.executeQuery(cql, uuid);
            Row row = rs.one();
            if (row != null) {
                hadoopClusterInfo = new HadoopClusterInfo();
                hadoopClusterInfo.setUid(row.getUUID("uid"));
                hadoopClusterInfo.setClusterName(row.getString("cluster_name"));
                hadoopClusterInfo.setNameNode(row.getUUID("name_node"));
                hadoopClusterInfo.setSecondaryNameNode(row.getUUID("secondary_name_node"));
                hadoopClusterInfo.setJobTracker(row.getUUID("job_tracker"));
                hadoopClusterInfo.setReplicationFactor(row.getInt("replication_factor"));
                hadoopClusterInfo.setDataNodes(row.getList("data_nodes", UUID.class));
                hadoopClusterInfo.setTaskTrackers(row.getList("task_trackers", UUID.class));
                hadoopClusterInfo.setIpMask(row.getString("ip_mask"));
            }

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Error in getHadoopClusterInfo(name)", ex);
        }
        return hadoopClusterInfo;
    }

}
