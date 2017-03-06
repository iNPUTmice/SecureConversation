package eu.siacs.conversations.xmpp.chatstate;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.jid.Jid;

/**
 * Created by muffu_7 on 3/5/17.
 */

public class MUCChatState {
    private Jid from;
    ChatState chatState;
    public MUCChatState(Jid FROM,ChatState CHATSTATE){
        from=FROM;
        chatState=CHATSTATE;
    }

    public Jid getFrom() {
        return from;
    }

    public void setFrom(Jid from) {
        this.from = from;
    }

    public ChatState getChatState() {
        return chatState;
    }

    public void setChatState(ChatState chatState) {
        this.chatState = chatState;
    }
}
