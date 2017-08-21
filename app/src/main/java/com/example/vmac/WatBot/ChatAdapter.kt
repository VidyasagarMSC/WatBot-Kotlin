package com.example.vmac.WatBot

/**
 * Created by VMac on 17/11/16.
 */

import android.graphics.Typeface
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import java.util.ArrayList


class ChatAdapter(private val messageArrayList: ArrayList<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    private val SELF = 100

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemView: View

        // view type is to identify where to render the chat message
        // left or right
        if (viewType == SELF) {
            // self message
            itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.chat_item_self, parent, false)
        } else {
            // WatBot message
            itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.chat_item_watson, parent, false)
        }


        return ViewHolder(itemView)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messageArrayList[position]
        if (message.id == "1") {
            return SELF
        }

        return position
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messageArrayList[position]
        message.message = message.message
        (holder as ViewHolder).message.text = message.message
    }

    override fun getItemCount(): Int {
        return messageArrayList.size
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        internal var message: TextView

        init {
            message = itemView.findViewById<TextView>(R.id.message) as TextView

            //TODO: Uncomment this if you want to use a custom Font
            /*String customFont = "Montserrat-Regular.ttf";
            Typeface typeface = Typeface.createFromAsset(itemView.getContext().getAssets(), customFont);
            message.setTypeface(typeface);*/

        }
    }


}