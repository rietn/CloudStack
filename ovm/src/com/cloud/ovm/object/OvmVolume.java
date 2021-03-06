package com.cloud.ovm.object;

import org.apache.xmlrpc.XmlRpcException;

public class OvmVolume extends OvmObject {
	public static class Details {
		public String name;
		public String uuid;
		public String poolUuid;
		public Long size;
		public String path;
		
		public String toJson() {
			return Coder.toJson(this);
		}
	}
	
	public static Details createDataDsik(Connection c, String poolUuid, String size, Boolean isRoot) throws XmlRpcException {
		Object[] params = {poolUuid, size, isRoot};
		String res = (String) c.call("OvmVolume.createDataDisk", params);
		Details result = Coder.fromJson(res, Details.class);
		return result;
	}
	
	public static Details createFromTemplate(Connection c, String poolUuid, String templateUrl) throws XmlRpcException {
		Object[] params = {poolUuid, templateUrl};
		String res = (String) c.callTimeoutInSec("OvmVolume.createFromTemplate", params, 3600*3);
		Details result = Coder.fromJson(res, Details.class);
		return result;
	}
	
	public static void destroy(Connection c, String poolUuid, String path) throws XmlRpcException {
		Object[] params = {poolUuid, path};
		c.call("OvmVolume.destroy", params);
	}
	
}
