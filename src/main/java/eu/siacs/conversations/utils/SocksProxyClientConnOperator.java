package eu.siacs.conversations.utils;

import org.apache.http.HttpHost;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Class derived from NetCipher
 * https://github.com/guardianproject/NetCipher
 * Apache 2.0 Licensed
 */
public class SocksProxyClientConnOperator extends DefaultClientConnectionOperator {

	private static final int CONNECT_TIMEOUT_MILLISECONDS = 60000;
	private static final int READ_TIMEOUT_MILLISECONDS = 60000;

	private final String mProxyHost;
	private final int mProxyPort;
	private final Socket socket;

	public SocksProxyClientConnOperator(final SchemeRegistry registry, final String proxyHost, final int proxyPort) {
		super(registry);

		mProxyHost = proxyHost;
		mProxyPort = proxyPort;
		socket = new Socket();
	}

	@Override
	public void openConnection(
			final OperatedClientConnection conn,
			final HttpHost target,
			final InetAddress local,
			final HttpContext context,
			final HttpParams params) throws IOException {
		if (conn == null || target == null || params == null) {
			throw new IllegalArgumentException("Required argument may not be null");
		}
		if (conn.isOpen()) {
			throw new IllegalStateException("Connection must not be open");
		}

		final Scheme scheme = schemeRegistry.getScheme(target.getSchemeName());
		final SocketFactory schemeSocketFactory = scheme.getSocketFactory();

		final int port = scheme.resolvePort(target.getPort());
		final String host = target.getHostName();

		try {
			conn.opening(socket, target);
			socket.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
			socket.connect(new InetSocketAddress(mProxyHost, mProxyPort), CONNECT_TIMEOUT_MILLISECONDS);

			final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
			outputStream.write((byte)0x04);
			outputStream.write((byte)0x01);
			outputStream.writeShort((short)port);
			outputStream.writeInt(0x01);
			outputStream.write((byte)0x00);
			outputStream.write(host.getBytes());
			outputStream.write((byte)0x00);

			final DataInput inputStream = new DataInputStream(socket.getInputStream());
			if (inputStream.readByte() != (byte)0x00 || inputStream.readByte() != (byte)0x5a) {
				throw new IOException("SOCKS4a connect failed");
			}
			inputStream.readShort();
			inputStream.readInt();

			conn.opening(socket,  target);
			socket.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
			prepareSocket(socket, context, params);
			conn.openCompleted(schemeSocketFactory.isSecure(socket), params);
		} catch (final IOException e) {
			try {
				socket.close();
			} catch (final IOException ignored) {}
			throw e;
		}
			}

	@Override
	public void updateSecureConnection(
			final OperatedClientConnection conn,
			final HttpHost target,
			final HttpContext context,
			final HttpParams params) throws IOException {
		throw new RuntimeException("operation not supported");
			}

	public Socket getSocket() {
		return socket;
	}
}
