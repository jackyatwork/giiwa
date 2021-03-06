/*
 * Copyright 2015 JIHU, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.giiwa.framework.bean;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.giiwa.core.base.Host;
import org.giiwa.core.bean.Bean;
import org.giiwa.core.bean.BeanDAO;
import org.giiwa.core.bean.Column;
import org.giiwa.core.bean.Helper.V;
import org.giiwa.core.conf.Local;
import org.giiwa.core.dfile.FileClient;
import org.giiwa.core.dfile.FileServer;
import org.giiwa.core.task.Task;
import org.giiwa.framework.web.Model;
import org.giiwa.framework.web.Model.HTTPMethod;
import org.giiwa.framework.web.Module;
import org.hyperic.sigar.CpuInfo;
import org.hyperic.sigar.CpuPerc;
import org.giiwa.core.bean.Table;
import org.giiwa.core.bean.X;

/**
 * The code bean, used to store special code linked with s1 and s2 fields
 * table="gi_code"
 * 
 * @author wujun
 *
 */
@Table(name = "gi_node")
public class Node extends Bean {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static final BeanDAO<String, Node> dao = BeanDAO.create(Node.class);

	public static final long LOST = 10 * 1000;

	@Column(name = X.ID, index = true)
	private String id;

	@Column(name = "ip")
	private String ip;

	@Column(name = "label")
	private String label;

	@Column(name = "url")
	private String url;

	@Column(name = "tasks")
	private int tasks;

	@Column(name = "uptime")
	private long uptime;

	@Column(name = "cores")
	private int cores; // cpu cores

	@Column(name = "usage")
	private int usage; // cpu usage

	@Column(name = "giiwa")
	private String giiwa;

	public int getUsage() {
		return usage;
	}

	public int getTasks() {
		return tasks;
	}

	public String getId() {
		return id;
	}

	public String getUrl() {
		return url;
	}

	public String getLabel() {
		return label;
	}

	public long getUptime() {
		return uptime;
	}

	public String getIp() {
		return ip;
	}

	public boolean isLocal() {
		return X.isSame(id, Local.id());
	}

	public static void touch(boolean force) {
		try {
			if (dao.exists(Local.id())) {
				// update
				if (force) {
					dao.update(Local.id(), getNodeInfo().append("tasks", Task.globaltask.get()));
				} else {

					V v = V.create().append("tasks", Task.globaltask.get());

					CpuPerc[] cc = Host.getCpuPerc();
					double user = 0;
					double sys = 0;
					for (CpuPerc c : cc) {
						/**
						 * user += c1.sys; <br/>
						 * user += c1.user;<br/>
						 * wait += c1.wait;<br/>
						 * nice += c1.nice;<br/>
						 * idle += c1.idle;<br/>
						 */
						user += c.getUser();
						sys += c.getSys();
					}
					v.append("usage", (int) ((user + sys) * 100 / cc.length));

					dao.update(Local.id(), v);
				}
			} else {
				// create
				dao.insert(getNodeInfo().append(X.ID, Local.id()).append("tasks", Task.globaltask.get()));
			}

		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	private static V getNodeInfo() {
		V v = V.create("uptime", Model.UPTIME).append("ip", Host.getLocalip());

		try {
			CpuInfo[] cc = Host.getCpuInfo();
			if (cc != null && cc.length > 0) {
				v.append("cores", cc[0].getTotalCores());
			}
			v.append("giiwa", Module.load("default").getVersion() + "." + Module.load("default").getBuild());
			v.append("os", Host.getOS().getName());
			v.append("mem", Host.getMem().getTotal());
			v.append("url", FileServer.URL);

			if (cc != null) {
				double user = 0;
				double sys = 0;
				for (CpuPerc c : Host.getCpuPerc()) {
					/**
					 * user += c1.sys; <br/>
					 * user += c1.user;<br/>
					 * wait += c1.wait;<br/>
					 * nice += c1.nice;<br/>
					 * idle += c1.idle;<br/>
					 */
					user += c.getUser();
					sys += c.getSys();
				}
				v.append("usage", (int) ((user + sys) * 100 / cc.length));
			}

		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return v;
	}

	public void forward(String uri, HttpServletRequest req, HttpServletResponse resp, HTTPMethod method) {
		try {
			FileClient.get(url).http(uri, req, resp, method, id);
		} catch (Exception e) {
			log.error(url, e);
		}
	}

}
