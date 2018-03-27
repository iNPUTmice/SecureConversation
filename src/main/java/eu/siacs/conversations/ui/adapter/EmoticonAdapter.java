package eu.siacs.conversations.ui.adapter;

import android.support.text.emoji.widget.EmojiAppCompatButton;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.siacs.conversations.R;

public class EmoticonAdapter extends RecyclerView.Adapter<EmoticonAdapter.EmoticonViewHolder> {

    private String[] mEmoticons;
    private OnEmoticonClickListener mOnEmoticonClickListener;

    public EmoticonAdapter(String[] emoticons, OnEmoticonClickListener onEmoticonClickListener) {
        this.mOnEmoticonClickListener = onEmoticonClickListener;
        this.mEmoticons = emoticons;
    }

    @Override
    public EmoticonViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycle_item_emoticon, parent, false);
        return new EmoticonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(EmoticonViewHolder holder, int position) {
        final String emoji = mEmoticons[position];
        holder.button.setText(emoji);
        holder.button.setOnClickListener(v -> {
            if(mOnEmoticonClickListener != null){
                mOnEmoticonClickListener.onEmoticonClick(emoji);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mEmoticons.length;
    }

    public interface OnEmoticonClickListener {
        void onEmoticonClick(String emoji);
    }

    public static class EmoticonViewHolder extends RecyclerView.ViewHolder {
        EmojiAppCompatButton button;

        public EmoticonViewHolder(View itemView) {
            super(itemView);
            button = itemView.findViewById(R.id.button);
        }
    }
}
