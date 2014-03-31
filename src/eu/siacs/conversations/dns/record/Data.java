package eu.siacs.conversations.dns.record;

import java.io.DataInputStream;
import java.io.IOException;

import eu.siacs.conversations.dns.Record.TYPE;

public interface Data {

    TYPE getType();

    byte[] toByteArray();

    void parse(DataInputStream dis, byte data[], int length) throws IOException;

}
