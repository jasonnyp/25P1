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
        holder.avgBPTV.text = "${currentItem.avgSysBP} / ${currentItem.avgDiaBP}"
        holder.homeBPTargetTV.text =
            "${currentItem.homeSysBPTarget} / ${currentItem.homeDiaBPTarget}"
        holder.clinicBPTargetTV.text =
            "${currentItem.clinicSysBPTarget} / ${currentItem.clinicDiaBPTarget}"
    }

    override fun getItemCount() = historyList.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        val dateTV: TextView = itemView.findViewById(R.id.dateTV)
        val avgBPTV: TextView = itemView.findViewById(R.id.avgBPTV)
        val homeBPTargetTV: TextView = itemView.findViewById(R.id.homeBPTargetTV)
        val clinicBPTargetTV: TextView = itemView.findViewById(R.id.clinicBPTargetTV)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItemClick(position)
            }
        }
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }
}