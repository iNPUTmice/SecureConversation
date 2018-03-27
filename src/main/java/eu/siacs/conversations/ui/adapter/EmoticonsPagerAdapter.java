package eu.siacs.conversations.ui.adapter;

import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v7.widget.GridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.PageEmoticonListBinding;

import static eu.siacs.conversations.utils.Emojis.EMOTICON_ARRAY;

/**
 * Created by mxf on 2018/3/22.
 */
public class EmoticonsPagerAdapter extends PagerAdapter {

    private EmoticonAdapter.OnEmoticonClickListener listener;

    public EmoticonsPagerAdapter(EmoticonAdapter.OnEmoticonClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return EMOTICON_ARRAY.length;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        PageEmoticonListBinding binding =
                DataBindingUtil.inflate(inflater, R.layout.page_emoticon_list, container, false);
        binding.recyclerView.setLayoutManager(new GridLayoutManager(container.getContext(), 5));
        binding.recyclerView.setAdapter(new EmoticonAdapter(EMOTICON_ARRAY[position], listener));
        container.addView(binding.getRoot());
        return binding.getRoot();
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }
}