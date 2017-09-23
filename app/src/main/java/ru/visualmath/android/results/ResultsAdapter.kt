package ru.visualmath.android.results

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import me.maximpestryakov.katexview.KatexView
import ru.visualmath.android.R
import ru.visualmath.android.api.model.QuestionBlock
import ru.visualmath.android.api.model.Results
import ru.visualmath.android.util.BindableViewHolder

internal class ResultsAdapter(private val questionBlock: QuestionBlock, private val results: Results) : RecyclerView.Adapter<ResultsAdapter.ResultViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) = holder.bind(position)

    override fun getItemCount() = results.answers.size

    internal inner class ResultViewHolder(itemView: View) : BindableViewHolder(itemView) {

        private val question: KatexView = itemView.findViewById(R.id.question)
        private val mark: TextView = itemView.findViewById(R.id.mark)

        override fun bind(position: Int) {
            question.text = questionBlock.questions[position].title
            mark.text = "Оценка: ${results.answers[position].mark}"
        }
    }
}
