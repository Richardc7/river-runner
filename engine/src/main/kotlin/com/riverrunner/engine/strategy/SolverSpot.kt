package com.riverrunner.engine.strategy

import com.riverrunner.engine.Action
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.Street

/** The inputs for a new river tree, and the observed path from its root to the hero. */
data class SolverSpot(
    val view: DecisionView,
    val startingPot: Int,
    val effectiveStack: Int,
    val riverActions: List<Action>,
) {
    companion object {
        fun from(view: DecisionView): SolverSpot? {
            if (view.mode != GameMode.CASH || view.street != Street.RIVER || view.cashRules.hasRake ||
                view.seats.size != 2 || view.seats.any { it.folded || it.allIn }) return null

            val prior = view.seats.map { it.committed - it.streetBet }
            val startingPot = prior.sum()
            if (prior[0] != prior[1] || startingPot <= 0 ||
                view.pot != view.seats.sumOf { it.committed }) return null

            val effectiveStack = view.seats.minOf { it.stack + it.streetBet }
            val riverActions = view.actions.filter { it.street == Street.RIVER }
            if (effectiveStack <= view.seats.maxOf { it.streetBet } || riverActions.any { it.type.isPost }) return null
            return SolverSpot(view, startingPot, effectiveStack, riverActions)
        }
    }
}
