package com.example.vmac.WatBot

import android.view.View

/**
 * Created by VMac on 06/01/17.
 */

interface ClickListener {
    fun onClick(view: View, position: Int)

    fun onLongClick(view: View, position: Int)
}