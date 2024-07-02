package com.singhealth.enhance.activities.settings.guide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.singhealth.enhance.R

class UserGuideAdapter(private var mList: List<UserGuides>) :
    RecyclerView.Adapter<UserGuideAdapter.GuideViewHolder>() {
    inner class GuideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val guideItemHeaderTV: TextView = itemView.findViewById(R.id.guideItemHeaderTV)
        val guideItemBodyTV: TextView = itemView.findViewById(R.id.guideItemBodyTV)
        val guideItemsLL: LinearLayout = itemView.findViewById(R.id.guideItemsLL)

        fun collapseExpandedView() {
            guideItemBodyTV.visibility = View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.accordion_guide_items, parent, false)
        return GuideViewHolder(view)
    }

    override fun onBindViewHolder(holder: GuideViewHolder, position: Int) {
        val languageData = mList[position]
        holder.guideItemHeaderTV.text = languageData.title
        holder.guideItemBodyTV.text = languageData.desc
        val isExpandable: Boolean = languageData.isExpandable
        holder.guideItemBodyTV.visibility = if (isExpandable) View.VISIBLE else View.GONE
        holder.guideItemsLL.setOnClickListener {
            isAnyItemExpanded(position)
            languageData.isExpandable = !languageData.isExpandable
            notifyItemChanged(position, Unit)
        }
    }

    private fun isAnyItemExpanded(position: Int) {
        val temp = mList.indexOfFirst {
            it.isExpandable
        }
        if (temp >= 0 && temp != position) {
            mList[temp].isExpandable = false
            notifyItemChanged(temp, 0)
        }
    }

    override fun onBindViewHolder(
        holder: GuideViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && payloads[0] == 0) {
            holder.collapseExpandedView()
        } else {
            super.onBindViewHolder(holder, position, payloads)

        }
    }

    override fun getItemCount(): Int {
        return mList.size
    }
}