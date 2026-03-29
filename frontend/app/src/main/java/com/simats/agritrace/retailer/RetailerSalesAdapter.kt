package com.simats.agritrace.retailer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simats.agritrace.R

class RetailerSalesAdapter(
    private val rows: MutableList<SaleRow>,
    private val onItemClick: ((SaleRow) -> Unit)? = null
) : RecyclerView.Adapter<RetailerSalesAdapter.VH>() {

    fun setAll(newRows: List<SaleRow>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_retailer_sale, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]

        holder.tvTitle.text = row.title
        holder.tvBatch.text = row.batchCode
        holder.tvAmount.text = row.amountText
        holder.tvSaleId.text = "ID: ${row.saleId}"
        holder.tvWhen.text = row.whenText

        // Ensure the whole card is clickable + ripple works
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener { onItemClick?.invoke(row) }
    }

    override fun getItemCount(): Int = rows.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvBatch: TextView = itemView.findViewById(R.id.tvBatch)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvSaleId: TextView = itemView.findViewById(R.id.tvSaleId)
        val tvWhen: TextView = itemView.findViewById(R.id.tvWhen)
    }
}
