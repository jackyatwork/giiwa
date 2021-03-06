package org.giiwa.framework.bean.m;

import org.giiwa.core.bean.Bean;
import org.giiwa.core.bean.BeanDAO;
import org.giiwa.core.bean.Column;
import org.giiwa.core.bean.Helper.V;
import org.giiwa.core.bean.Helper.W;
import org.giiwa.core.bean.Table;
import org.giiwa.core.bean.UID;
import org.giiwa.core.bean.X;
import org.giiwa.core.json.JSON;

@Table(name = "gi_m_mem")
public class _Memory extends Bean {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static BeanDAO<String, _Memory> dao = BeanDAO.create(_Memory.class);

	@Column(name = X.ID)
	String id;

	@Column(name = "node")
	String node;

	@Column(name = "total")
	long total;

	@Column(name = "used")
	long used;

	@Column(name = "usage")
	int usage;

	@Column(name = "free")
	long free;

	@Column(name = "swaptotal")
	long swaptotal;

	@Column(name = "swapfree")
	long swapfree;

	
	public long getUsed() {
		return used;
	}

	public long getFree() {
		return free;
	}

	public static void update(String node, JSON jo) {
		// insert or update
		try {
			V v = V.fromJSON(jo).remove("_id", X.ID);

			dao.delete(W.create("node", node));
			// insert
			dao.insert(v.copy().force(X.ID, UID.id(node)).force("node", node));

			Record.dao.insert(
					v.copy().force(X.ID, UID.id(node, System.currentTimeMillis())).force("node", node));

		} catch (Exception e) {
			log.error(jo, e);
		}
	}

	@Table(name = "gi_m_mem_record")
	public static class Record extends _Memory {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		public static BeanDAO<String, Record> dao = BeanDAO.create(Record.class);

		public void cleanup() {
			dao.delete(W.create().and("created", System.currentTimeMillis() - X.AWEEK, W.OP.lt));
		}

	}

	@Override
	public void cleanup() {
		dao.cleanup();
		Record.dao.cleanup();
	}

}
