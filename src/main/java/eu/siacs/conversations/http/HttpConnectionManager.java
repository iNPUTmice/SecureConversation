package eu.siacs.conversations.http;

import java.net.UnknownHostException;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;

public class HttpConnectionManager extends AbstractConnectionManager {

	public HttpConnectionManager(final XmppConnectionService service) {
		super(service);
	}

	private final Collection<HttpConnection> connections = new CopyOnWriteArrayList<>();

	public HttpConnection createNewConnection(final Message message) throws UnknownHostException {
		final HttpConnection connection = new HttpConnection(this);
		connection.init(message);
		this.connections.add(connection);
		return connection;
	}

	public void finishConnection(final HttpConnection connection) {
		this.connections.remove(connection);
	}
}
