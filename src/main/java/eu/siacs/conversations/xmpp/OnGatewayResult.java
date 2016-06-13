package eu.siacs.conversations.xmpp;

public interface OnGatewayResult {
	// if result is null, there was an error
	// errorText may or may not be set
	public void onGatewayResult(String result, String errorText);
}
