package com.quizduel.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.quizduel.app.R
import com.quizduel.app.data.model.Topic
import com.quizduel.app.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val categories: List<Topic>,
    private val onCategoryClick: (Topic) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var selectedPosition = 0

    inner class CategoryViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        val isSelected = position == selectedPosition
        val context = holder.itemView.context

        holder.binding.tvCategoryName.text = category.name
        holder.binding.tvCategoryIcon.text = category.icon

        holder.binding.cardCategory.setCardBackgroundColor(
            if (isSelected) context.getColor(R.color.clay_primary) else context.getColor(R.color.clay_white)
        )
        holder.binding.tvCategoryName.setTextColor(
            if (isSelected) context.getColor(R.color.clay_white) else context.getColor(R.color.clay_text_dark)
        )

        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            onCategoryClick(category)
        }
    }

    override fun getItemCount() = categories.size
}