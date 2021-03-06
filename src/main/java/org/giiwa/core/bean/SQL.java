package org.giiwa.core.bean;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Stack;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.core.base.StringFinder;
import org.giiwa.core.bean.Beans;
import org.giiwa.core.bean.X;
import org.giiwa.core.bean.Helper.DBHelper;
import org.giiwa.core.bean.Helper.W;
import org.giiwa.core.bean.helper.RDSHelper;
import org.giiwa.core.dle.JS;
import org.giiwa.core.json.JSON;
import org.giiwa.framework.bean.GLog;

public class SQL {

	static Log log = LogFactory.getLog(SQL.class);

	/**
	 * 
	 * @param h
	 * @param sql
	 *            "select * from
	 *            <table>
	 *            where a=1 orderby b <desc> offset 1 limit 10";
	 * @return
	 */
	public static List<Bean> query(DBHelper h, String sql) {

		try {
			JSON q = _sql(StringFinder.create(sql));

			log.debug("q=" + q);

			W q1 = where2W(StringFinder.create(q.getString("where")));
			if (q.containsKey("orderby")) {
				String order = q.getString("orderby");
				if (!X.isEmpty(order)) {
					String[] ss = X.split(order, ",");
					if (ss != null) {
						if (q1 == null)
							q1 = W.create();

						for (String s : ss) {
							String[] ss1 = X.split(s, " ");
							if (ss1.length > 1) {
								if (X.isSame(ss1[1], "desc")) {
									q1.sort(ss1[0], -1);
								} else {
									q1.sort(ss1[0], 1);
								}
							} else {
								q1.sort(ss1[0], 1);
							}
						}
					}
				}
			}

			Beans<Bean> bs = h.load(q.getString("tablename"), null, q1, q.getInt("offset", 0), q.getInt("limit", 10),
					Bean.class, X.EMPTY);

			return bs;

		} catch (Exception e) {
			log.error(sql, e);
			GLog.applog.error("sql", "query", sql, e, null, null);
		}
		return null;

	}

	private static JSON _sql(StringFinder sf) {

		log.debug("select ...");
		sf.next(" ");
		sf.trim();
		String cols = sf.nextTo("from");
		if (X.isEmpty(cols)) {
			return null;
		}

		JSON r = JSON.create();
		r.put("cols", cols);

		sf.trim();
		sf.next(" ");
		sf.trim();
		String table = sf.next(" ");
		if (X.isEmpty(table)) {
			return null;
		}
		r.put("tablename", table);

		return _condition(sf, r);
	}

	private static JSON _condition(StringFinder sf, JSON r) {
		sf.trim();
		String s = sf.next(" ");
		if (X.isEmpty(s)) {
			return r;
		}

		if (X.isSame(s, "where")) {
			sf.trim();
			String w = sf.nextTo("(group|order|offset|limit)");
			if (!X.isEmpty(w)) {
				r.put("where", w);
				return _condition(sf, r);
			} else {
				return null;
			}
		} else if (X.isSame(s, "groupby")) {
			sf.trim();
			String g = sf.nextTo("(order|offset|limit)");
			if (!X.isEmpty(g)) {
				r.put("groupby", g);
				return _condition(sf, r);
			} else {
				return null;
			}
		} else if (X.isSame(s, "orderby")) {
			sf.trim();
			String o = sf.nextTo("(offset|limit)");
			if (!X.isEmpty(o)) {
				r.put("orderby", o);
				return _condition(sf, r);
			} else {
				return null;
			}
		} else if (X.isSame(s, "offset")) {
			sf.trim();
			String o = sf.nextTo("(limit)");
			if (!X.isEmpty(o)) {
				r.put("offset", X.toInt(o));
				return _condition(sf, r);
			} else {
				return null;
			}
		} else if (X.isSame(s, "limit")) {
			sf.trim();
			String o = sf.next(" ");
			if (!X.isEmpty(o)) {
				r.put("limit", X.toInt(o));
				return _condition(sf, r);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	public static W where2W(StringFinder s) throws Exception {
		// “name=’2’ and age>10 or (name=‘1‘ and age<20) or name like ‘dd%’;
		if (s == null || !s.hasMore()) {
			return W.create();
		}

		Stack<String> conn = new Stack<String>();
		W q = W.create();

		while (s.hasMore()) {
			s.trim();
			char c = s.next();
			if (c == '(') {
				W q1 = where2W(s);
				if (q1 != null) {
					String o = conn.isEmpty() ? "and" : conn.pop();
					if (X.isSame(o, "and")) {
						q.and(q1);
					} else {
						q.or(q1);
					}
				}
			} else if (c == ')') {
				return q;
			} else {
				s.skip(-1);

				StringFinder s1 = StringFinder.create(s.nextTo("(\\)|and|or)"));
				s.trim();

				// if (!s.hasMore())
				// throw new Exception("expect [op] more after [" + s1.toString() +
				// "]");

				String name = s1.nextTo("( |=|>|<|like)");

				s1.trim();
				c = s1.next();
				String op = null;
				if (c == '=') {
					op = "=";
				} else if (c == '>' || c == '<' || c == '!') {
					char c1 = s1.next();
					if (c1 == '=') {
						op = Character.toString(c) + c1;
					} else {
						s1.skip(-1);
						op = Character.toString(c);
					}
				} else {
					// like ?
					s1.skip(-1);
					op = s.next(" ");
				}

				s1.trim();
				c = s1.next();

				Object value = null;
				if (c == '\'') {
					value = s1.pair('\'');
				} else {
					s1.skip(-1);

					String s2 = s1.remain();
					value = JS.calculate(s2);
				}

				String o = conn.isEmpty() ? "and" : conn.pop();
				if (X.isSame(o, "and")) {
					if (X.isSame(op, "=")) {
						q.and(name, value);
					} else if (X.isSame(op, "!=")) {
						q.and(name, value, W.OP.neq);
					} else if (X.isSame(op, ">")) {
						q.and(name, value, W.OP.gt);
					} else if (X.isSame(op, ">=")) {
						q.and(name, value, W.OP.gte);
					} else if (X.isSame(op, "<")) {
						q.and(name, value, W.OP.lt);
					} else if (X.isSame(op, "<=")) {
						q.and(name, value, W.OP.lte);
					} else if (X.isSame(op, "<")) {
						q.and(name, value, W.OP.lt);
					} else if (X.isSame(op, "like")) {
						q.and(name, value, W.OP.like);
					}
				} else {
					if (X.isSame(op, "=")) {
						q.or(name, value);
					} else if (X.isSame(op, "!=")) {
						q.or(name, value, W.OP.neq);
					} else if (X.isSame(op, ">")) {
						q.or(name, value, W.OP.gt);
					} else if (X.isSame(op, ">=")) {
						q.or(name, value, W.OP.gte);
					} else if (X.isSame(op, "<")) {
						q.or(name, value, W.OP.lt);
					} else if (X.isSame(op, "<=")) {
						q.or(name, value, W.OP.lte);
					} else if (X.isSame(op, "<")) {
						q.or(name, value, W.OP.lt);
					} else if (X.isSame(op, "like")) {
						q.or(name, value, W.OP.like);
					}
				}
			}

			s.trim();
			// get conn
			if (s.hasMore()) {
				c = s.next();
				if (c == ')') {
					return q;
				}

				s.skip(-1);
				String s1 = s.next(" ");
				conn.push(s1);
			}

		}

		return q;
	}

	/**
	 * 
	 * @param sql
	 * @return
	 * @throws SQLException
	 */
	public static Bean get(DBHelper h, String sql) throws SQLException {

		try {

			JSON q = _sql(StringFinder.create(sql));
			W q1 = where2W(StringFinder.create(q.getString("where")));
			if (q.containsKey("orderby")) {
				String order = q.getString("orderby");
				if (!X.isEmpty(order)) {
					String[] ss = X.split(order, ",");
					if (ss != null) {
						if (q1 == null)
							q1 = W.create();

						for (String s : ss) {
							String[] ss1 = X.split(s, " ");
							if (ss1.length > 1) {
								if (X.isSame(ss1[1], "desc")) {
									q1.sort(ss1[0], -1);
								} else {
									q1.sort(ss1[0], 1);
								}
							} else {
								q1.sort(ss1[0], 1);
							}
						}
					}
				}
			}

			return h.load(q.getString("tablename"), null, q1, Bean.class, X.EMPTY);
		} catch (Exception e) {
			log.error(sql, e);
		}
		return null;

	}

	public static int execute(DBHelper h, String sql) throws SQLException {

		Statement stat = null;

		try {
			Connection con = null;
			if (h instanceof RDSHelper) {
				con = ((RDSHelper) h).getConnection();
			}
			if (con != null) {
				stat = con.createStatement();
				return stat.executeUpdate(sql);
			}
		} finally {
			RDSHelper.inst.close(stat);
		}
		return -1;
	}

	public static void main(String[] args) throws Exception {
		String s = "a>10/2*5 and b>11";
		W q = SQL.where2W(StringFinder.create(s));
		System.out.println(q);

		// SQL.query(null, "select *");
		JSON q1 = _sql(StringFinder.create("select * from gi_oplog limit 10"));
		System.out.println(q1);
	}

	// private static String[] KW = { "and", "or", "like", "=", "!=", ">", ">=",
	// "<", "<=" };

}
