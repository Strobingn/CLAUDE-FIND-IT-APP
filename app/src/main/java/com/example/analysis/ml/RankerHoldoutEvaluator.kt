package com.example.analysis.ml

/**
 * Held-out accuracy from a spatially separate fold the final model never trained on, distinct
 * from [RankerTrainingResult.accuracy]'s in-sample number — plus how many rejected examples the
 * holdout model still scores as likely-productive, the hard negatives worth a second look.
 */
data class HoldoutEvaluation(val heldOutAccuracy: Float?, val hardNegativeCount: Int)

/**
 * Trains a throwaway model on all but one spatial fold, then scores that held-out fold with it.
 * Ties together [SpatialFoldSplitter] (never split at random), [RankerTrainer] (the same trainer
 * that builds the real model), and [HardNegativeMiner] (surfaces confidently-wrong rejections) —
 * the three previously-unused pieces of the documented training pipeline.
 */
object RankerHoldoutEvaluator {
    fun evaluate(
        examples: List<RankerTrainingExample>,
        locations: List<SpatialFoldSplitter.FoldLocation>,
        featureNames: List<String>,
        foldCount: Int = 4,
        hardNegativeLimit: Int = 5,
    ): HoldoutEvaluation? {
        require(examples.size == locations.size) { "every example needs a matching location" }
        // Too few points for any spatial fold to be a meaningful held-out check.
        if (examples.size < 6) return null

        val indices = examples.indices.toList()
        val folds = SpatialFoldSplitter.split(indices, foldCount) { locations[it] }
        val nonEmptyFolds = folds.filter { it.isNotEmpty() }
        // Everything landed in one spatial block (e.g. a single tight cluster of field checks) —
        // there's no real "elsewhere" to hold out, so report no accuracy rather than a fake one.
        if (nonEmptyFolds.size < 2) return null

        val evalIndices = nonEmptyFolds.maxBy { it.size }.toSet()
        val trainExamples = indices.filterNot { it in evalIndices }.map { examples[it] }
        val trainConfirmed = trainExamples.count { it.productive }
        val trainRejected = trainExamples.size - trainConfirmed
        if (trainConfirmed == 0 || trainRejected == 0) return null

        val holdoutModel = RankerTrainer.train(
            examples = trainExamples,
            modelVersion = "holdout-eval",
            featureNames = featureNames,
        ).ranker

        val evalExamples = evalIndices.map { examples[it] }
        val correct = evalExamples.count { (holdoutModel.probability(it.features) >= 0.5f) == it.productive }
        val hardNegatives = HardNegativeMiner.select(
            rejectedExamples = examples.filter { !it.productive },
            score = { holdoutModel.probability(it.features) },
            limit = hardNegativeLimit,
        ).count { holdoutModel.probability(it.features) >= 0.5f }

        return HoldoutEvaluation(
            heldOutAccuracy = correct.toFloat() / evalExamples.size,
            hardNegativeCount = hardNegatives,
        )
    }
}
