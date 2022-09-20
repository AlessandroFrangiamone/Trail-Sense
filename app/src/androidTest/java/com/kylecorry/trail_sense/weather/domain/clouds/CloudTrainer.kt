package com.kylecorry.trail_sense.weather.domain.clouds

import androidx.test.platform.app.InstrumentationRegistry
import com.kylecorry.andromeda.csv.CSVConvert
import com.kylecorry.andromeda.files.LocalFiles
import com.kylecorry.sol.math.SolMath
import com.kylecorry.sol.math.algebra.createMatrix
import com.kylecorry.sol.math.classifiers.IClassifier
import com.kylecorry.sol.math.classifiers.LogisticRegressionClassifier
import org.junit.Test

class CloudTrainer {

    @Test
    fun train() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = LocalFiles.read(context, "debug/clouds/clouds.csv")
        val csv = CSVConvert.parse(file)

        val x = mutableListOf<List<Float>>()
        val y = mutableListOf<Int>()

        csv.shuffled().forEach {
            y.add(it[0].toInt())
            // Remove label
            x.add(it.subList(1, it.size).map { it.toFloat() })
        }

        val clf = LogisticRegressionClassifier.fromWeights(
            createMatrix(
                x.first().size,
                10
            ) { _, _ -> Math.random().toFloat() * 0.1f })

        clf.fitClasses(
            x,
            y,
            1000,
            learningRate = 0.1f,
            batchSize = 5
        ) { error, epoch ->
            if (epoch % 20 == 0) {
                println("$epoch: $error")
            }
        }

        // Record training data
        val rows =
            clf.dump().joinToString(",\n") { "arrayOf(${it.joinToString(",") { "${it}f" }}" }
        LocalFiles.write(
            context,
            "debug/clouds/weights.csv",
            "arrayOf($rows)"
        )

        println(score(clf, x, y))
    }

    private fun score(classifier: IClassifier, x: List<List<Float>>, y: List<Int>): Float {
        var correct = 0
        x.zip(y).forEach {
            val pred = SolMath.argmax(classifier.classify(it.first))
            if (pred == it.second) {
                correct++
            }
        }
        return correct / x.size.toFloat()
    }


}