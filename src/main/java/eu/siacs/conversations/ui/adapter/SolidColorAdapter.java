package eu.siacs.conversations.ui.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.siacs.conversations.R;

public class SolidColorAdapter extends RecyclerView.Adapter<SolidColorAdapter.SolidColorViewHolder> {

    private OnSolidColorSelected listener;
    private int[] solidColorList;

    public SolidColorAdapter(OnSolidColorSelected listener, int[] solidColorList) {
        this.listener = listener;
        this.solidColorList = solidColorList;
    }

    @NonNull
    @Override
    public SolidColorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.solid_color_layout, parent, false);
        return new SolidColorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SolidColorViewHolder holder, int position) {
        holder.colorView.setBackgroundColor(solidColorList[position]);
        holder.colorView.setOnClickListener(view -> {
            if (listener != null) {
                listener.onSolidColorSelected(solidColorList[holder.getAdapterPosition()]);
            }
        });
    }

    @Override
    public int getItemCount() {
        return solidColorList.length;
    }

    public static class SolidColorViewHolder extends RecyclerView.ViewHolder {
        private View colorView;

        public SolidColorViewHolder(View itemView) {
            super(itemView);
            colorView = itemView.findViewById(R.id.solid_color_view);
        }
    }

    public interface OnSolidColorSelected {
         void onSolidColorSelected(int color);
    }
}
