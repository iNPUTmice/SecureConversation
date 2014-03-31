package eu.siacs.conversations.dns.record;

import eu.siacs.conversations.dns.Record.TYPE;

public class NS extends CNAME {

    @Override
    public TYPE getType() {
        return TYPE.NS;
    }

}
