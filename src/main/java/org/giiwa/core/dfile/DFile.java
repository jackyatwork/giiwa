package org.giiwa.core.dfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.app.web.admin.dfile;
import org.giiwa.core.base.IOUtil;
import org.giiwa.core.bean.X;
import org.giiwa.core.json.JSON;
import org.giiwa.framework.bean.Disk;
import org.giiwa.framework.bean.GLog;
import org.giiwa.framework.bean.Node;

/**
 * Demo bean
 * 
 * @author joe
 * 
 */

public class DFile {

	private static Log log = LogFactory.getLog(DFile.class);

	private long disk;

	private String filename;

	private String url;

	private transient String path;
	private transient Node node_obj;
	private transient Disk disk_obj;
	private transient JSON info;

	public String getFilename() {
		return filename;
	}

	public Node getNode_obj() {
		if (node_obj == null) {
			check();
		}
		return node_obj;
	}

	public Disk getDisk_obj() {
		if (disk_obj == null) {
			check();
		}
		return disk_obj;
	}

	public boolean check() {

		if (disk_obj == null && disk > 0) {
			disk_obj = Disk.dao.load(disk);
		}

		if (disk_obj != null) {
			path = disk_obj.getPath();
			node_obj = disk_obj.getNode_obj();

			if (node_obj != null) {
				url = node_obj.getUrl();

				return true;
			}
		}

		return false;
	}

	public boolean exists() {
		check();

		getInfo();
		return info == null ? false : info.getInt("e") == 1;
	}

	public String getAbsolutePath() {
		return X.getCanonicalPath(path + "/" + filename);
	}

	public boolean delete() {
		return delete(-1);
	}

	public boolean delete(long age) {
		check();

		try {

			return FileClient.get(url).delete(path, filename, age);

		} catch (Exception e) {
			log.error(url, e);
		} finally {
			// dao.delete(W.create("disk", disk).and("filename", filename));
		}

		return false;
	}

	public InputStream getInputStream() throws FileNotFoundException {
		check();

		return DFileInputStream.create(this.getDisk_obj(), filename);
	}

	public OutputStream getOutputStream() throws FileNotFoundException {
		return this.getOutputStream(0);
	}

	public OutputStream getOutputStream(long offset) throws FileNotFoundException {

		check();

		if (offset == 0) {
			GLog.applog.info(dfile.class, "put", filename, null, null);
		}

		return DFileOutputStream.create(this.getDisk_obj(), filename, offset);
	}

	public boolean mkdirs() {
		check();

		try {
			return FileClient.get(url).mkdirs(path, this.filename);
		} catch (Exception e) {
			log.error(url, e);
		}
		return true;
	}

	public DFile getParentFile() {
		int i = filename.lastIndexOf("/");
		if (i > 0) {
			return create(disk_obj, filename.substring(0, i));
		} else {
			return null;
		}
	}

	private JSON getInfo() {
		if (info == null) {
			try {
				info = FileClient.get(url).info(path, filename);
			} catch (IOException e) {
				log.error(url, e);
			}
		}
		return info;
	}

	public boolean isDirectory() {
		check();

		getInfo();
		return info == null ? false : info.getInt("f") != 1;
	}

	public boolean isFile() {
		check();

		getInfo();
		return info == null ? false : info.getInt("f") == 1;
	}

	public String getName() {
		String[] ss = X.split(filename, "[/]");
		if (ss != null && ss.length > 0) {
			return ss[ss.length - 1];
		}
		return X.EMPTY;
	}

	public DFile[] listFiles() throws Exception {
		check();

		JSON jo = FileClient.get(url).list(path, filename);
		Collection<JSON> l1 = jo.getList("list");

		DFile[] l2 = new DFile[l1.size()];
		int i = 0;

		for (JSON j1 : l1) {
			DFile d1 = DFile.create(disk_obj, X.getCanonicalPath("/" + filename + "/" + j1.getString("name")), j1);
			l2[i++] = d1;
		}

		return l2;
	}

	public long lastModified() {

		check();

		getInfo();
		return info == null ? 0 : info.getLong("u");
	}

	public String getCanonicalPath() {
		return filename;
	}

	public long length() {
		check();

		getInfo();

		return info == null ? 0 : info.getLong("l");
	}

	public boolean renameTo(DFile file) {

		try {
			return FileClient.get(url).move(path, filename, file.path, file.filename);
		} catch (Exception e) {
			log.error(url, e);
		}
		return false;
	}

	public static DFile create(Disk d, String filename) {
		return create(d, filename, null);
	}

	public static DFile create(Disk d, String filename, JSON info) {
		DFile e = new DFile();

		e.disk = d.getId();
		e.filename = filename;

		e.disk_obj = d;
		e.node_obj = d.getNode_obj();
		e.url = d.getNode_obj().getUrl();
		e.path = d.getPath();
		e.info = info;

		return e;

	}

	@Override
	public String toString() {
		return "DFile [" + url + filename + ", exists=" + this.exists() + ", dir=" + this.isDirectory() + "]";
	}

	/**
	 * copy the file and upload to disk
	 * 
	 * @param f
	 * @return
	 */
	public long upload(File f) {
		try {
			return IOUtil.copy(new FileInputStream(f), this.getOutputStream());
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return -1;
	}

	public long upload(InputStream in) {
		return upload(0, in);
	}

	/**
	 * upload the inputsteam to the file
	 * 
	 * @param in
	 * @return
	 */
	public long upload(long pos, InputStream in) {
		try {
			return IOUtil.copy(in, this.getOutputStream(pos));
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return -1;
	}

	/**
	 * download the file to local file
	 * 
	 * @param f
	 * @return
	 */
	public long download(File f) {
		try {
			f.getParentFile().mkdirs();
			return IOUtil.copy(this.getInputStream(), new FileOutputStream(f));
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return -1;
	}

}
