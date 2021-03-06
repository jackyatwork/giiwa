package org.giiwa.core.dfile.command;

import org.giiwa.core.bean.X;
import org.giiwa.core.dfile.ICommand;
import org.giiwa.core.dfile.MockRequest;
import org.giiwa.core.dfile.MockResponse;
import org.giiwa.core.json.JSON;
import org.giiwa.core.nio.IResponseHandler;
import org.giiwa.core.nio.Request;
import org.giiwa.core.nio.Response;
import org.giiwa.framework.web.Controller;
import org.giiwa.framework.web.Model;

public class HTTP implements ICommand {

	@Override
	public void process(Request in, IResponseHandler handler) {

		int m = in.readInt();
		String uri = in.readString();
		JSON head = JSON.fromObject(in.readString());
		JSON body = JSON.fromObject(in.readString());

		// log.debug("head=" + head.toString());
		// log.debug("body=" + body.toString());

		MockResponse resp = MockResponse.create();
		Controller.dispatch(uri, MockRequest.create(uri, head, body), resp, new Model.HTTPMethod(m));

		Response out = Response.create(in.seq);
		out.writeInt(resp.status);
		out.writeString(resp.head.toString());
		out.writeBytes(resp.out.toByteArray());
		X.close(resp);

		handler.send(out);

	}

}
