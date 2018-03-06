package eu.siacs.conversations.ui.adapter;

import android.support.text.emoji.widget.EmojiButton;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.EmojiWrapper;
import eu.siacs.conversations.utils.Emoticons;

public class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder> {

    private List<String> emojis;
    private Emoticons.EmojiSet emojiSet;
    private OnEmojiClickListener onEmojiClickListener;

    public EmojiAdapter(Emoticons.EmojiSet emojiSet,OnEmojiClickListener onEmojiClickListener) {
        this.onEmojiClickListener = onEmojiClickListener;
        this.emojis = new ArrayList<>();
        this.emojiSet = emojiSet;
    }

    @Override
    public EmojiViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View inflate = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_emoji, parent, false);
        return new EmojiViewHolder(inflate);
    }

    @Override
    public void onBindViewHolder(EmojiViewHolder holder, int position) {
        String emoji = emojiSet.get(position);
        holder.emoji_button.setText(EmojiWrapper.transform(emoji));
        holder.emoji_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onEmojiClickListener.onEmojiClick(emoji);
            }
        });
    }

    @Override
    public int getItemCount() {
        return emojiSet.size();
    }

    public interface OnEmojiClickListener {
        void onEmojiClick(String emoji);
    }

    public static class EmojiViewHolder extends RecyclerView.ViewHolder {
        EmojiButton emoji_button;

        public EmojiViewHolder(View itemView) {
            super(itemView);
            emoji_button = itemView.findViewById(R.id.emoji_button);
        }
    }
}
