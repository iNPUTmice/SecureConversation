package eu.siacs.conversations.ui;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.FragmentConversationWallpaperBottomSheetBinding;

public class ConversationWallpaperBottomSheet extends BottomSheetDialogFragment {

    private OnWallpaperActionClicked mListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentConversationWallpaperBottomSheetBinding binding = DataBindingUtil.inflate(getActivity().getLayoutInflater(), R.layout.fragment_conversation_wallpaper_bottom_sheet, null, false);
        binding.addWallpaper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onAddWallpaperClicked();
                    dismiss();
                }
            }
        });
        binding.removeWallpaper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onRemoveWallpaperClicked();
                    dismiss();
                }
            }
        });
        return binding.getRoot();
    }

    public void setWallpaperActionListener(OnWallpaperActionClicked mListener) {
        this.mListener = mListener;
    }

    public interface OnWallpaperActionClicked {

        void onAddWallpaperClicked();

        void onRemoveWallpaperClicked();
    }
}
