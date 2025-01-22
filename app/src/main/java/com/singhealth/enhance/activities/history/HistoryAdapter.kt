package com.singhealth.enhance.activities.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.singhealth.enhance.R

class HistoryAdapter(
    private val historyList: List<HistoryData>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_item, parent, false)
        return HistoryViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val currentItem = historyList[position]

        holder.dateTV.text = currentItem.dateFormatted.toString()
        holder.avgBPTV.text = String.format("%s / %s", currentItem.avgSysBP, currentItem.avgDiaBP)
        holder.clinicTV.text = String.format("%s / %s", currentItem.clinicSysBP, currentItem.clinicDiaBP)
        if (currentItem.sevenDay) {
            holder.statusContainer.text = "7-Day"
            holder.statusContainer.setBackgroundResource(R.color.lifestyle) // Replace with your 7-day color
        } else {
            holder.statusContainer.text = "General"
            holder.statusContainer.setBackgroundResource(R.color.medical) // Replace with your General color
        }
//        holder.homeBPTargetTV.text = String.format("%s / %s", currentItem.homeSysBPTarget, currentItem.homeDiaBPTarget)
//        holder.clinicBPTargetTV.text = String.format("%s / %s", currentItem.clinicSysBPTarget, currentItem.clinicDiaBPTarget)
    }

    override fun getItemCount() = historyList.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        val dateTV: TextView = itemView.findViewById(R.id.dateTV)
        val avgBPTV: TextView = itemView.findViewById(R.id.avgBPTV)
        val clinicTV: TextView = itemView.findViewById(R.id.clinicTV)
        val statusContainer: TextView = itemView.findViewById(R.id.statusContainer)
//        val homeBPTargetTV: TextView = itemView.findViewById(R.id.homeBPTargetTV)
//        val clinicBPTargetTV: TextView = itemView.findViewById(R.id.clinicBPTargetTV)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = bindingAdapterPosition // adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItemClick(position)
            }
        }
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }
}