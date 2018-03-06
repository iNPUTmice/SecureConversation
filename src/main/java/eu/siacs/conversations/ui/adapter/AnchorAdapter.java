package eu.siacs.conversations.ui.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import eu.siacs.conversations.R;

public class AnchorAdapter extends RecyclerView.Adapter<AnchorAdapter.AnchorViewHolder> {

    private List<Integer> anchors;
    private OnAnchorClickListener onAnchorClickListener;

    public AnchorAdapter(List<Integer> anchors,OnAnchorClickListener onAnchorClickListener) {
        this.onAnchorClickListener = onAnchorClickListener;
        this.anchors = anchors;
    }

    @Override
    public AnchorViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View inflate = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_tag, parent, false);
        return new AnchorViewHolder(inflate);
    }

    @Override
    public void onBindViewHolder(AnchorViewHolder holder, int position) {
        final Integer anchor = anchors.get(position);
        ((TextView) holder.itemView).setText(anchor+"");
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onAnchorClickListener.onAnchorClick(anchor);
            }
        });
    }

    @Override
    public int getItemCount() {
        return anchors.size();
    }

    public interface OnAnchorClickListener {
        void onAnchorClick(int anchor);
    }

    public static class AnchorViewHolder extends RecyclerView.ViewHolder {
        public AnchorViewHolder(View itemView) {
            super(itemView);
        }
    }
}
