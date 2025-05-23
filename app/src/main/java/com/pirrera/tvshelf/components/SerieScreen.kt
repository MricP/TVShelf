package com.pirrera.tvshelf.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pirrera.tvshelf.R
import com.pirrera.tvshelf.model.WatchState
import com.pirrera.tvshelf.ui.theme.Background
import com.pirrera.tvshelf.ui.theme.Green
import com.pirrera.tvshelf.ui.theme.Primary
import com.pirrera.tvshelf.ui.theme.Red
import com.pirrera.tvshelf.ui.theme.Secondary
import com.pirrera.tvshelf.ui.theme.Tertiary
import com.pirrera.tvshelf.ui.theme.Yellow
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator


@OptIn(ExperimentalMaterial3Api::class)
@Destination
@Composable
fun SerieScreen(
    navigator: DestinationsNavigator,
    serieId : String,
    serieName: String,
    serieOverview: String,
    posterPath: String?,
    voteAvg: String,
    airDate: String?
) {

    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var userRating by remember { mutableStateOf<Int?>(null) }

    var watchState by rememberSaveable { mutableStateOf(WatchState.WatchNow) }
    val onRatingConfirmed: () -> Unit = {
        if (watchState == WatchState.WatchNow) {
            watchState = WatchState.Watching

            // update firestore
            val db = FirebaseFirestore.getInstance()
            val userDoc = db.collection("users").document(userId)
            val serieDoc = userDoc.collection("showsStatus").document(serieId)
            val updateData = mapOf(
                "watchState" to watchState.name,  // wathing
                "posterPath" to (posterPath ?: "")
            )

            serieDoc.set(updateData)
                .addOnSuccessListener { Log.d("Firestore", "Watch state saved successfully") }
                .addOnFailureListener { e -> Log.e("Firestore", "Error saving watch state: ${e.message}") }
        }
    }


    LaunchedEffect(userId, serieId) {
        fetchUserRating(db, userId, serieId) { rating ->
            userRating = rating
        }
    }

    LaunchedEffect(userId, serieId) {
        val userDoc = db.collection("users").document(userId)
        userDoc.collection("showsStatus").document(serieId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val state = document.getString("watchState")
                    watchState = state?.let { WatchState.valueOf(it) } ?: WatchState.WatchNow
                }
            }
            .addOnFailureListener {
                Log.e("Firestore", "Error fetching watch state: ${it.message}")
            }
    }


    fun resetRating(db: FirebaseFirestore, userId: String, showId: String, onRatingReset: () -> Unit) {
        val reviewRef = db.collection("reviews")
            .whereEqualTo("userId", userId)
            .whereEqualTo("showId", showId)

        reviewRef.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                val docId = documents.documents.first().id
                db.collection("reviews").document(docId)
                    .delete() // delete review if exists
                    .addOnSuccessListener {
                        deleteRatingAndUpdateAverage(db, userId, userRating ?: 0)
                        Log.d("Firestore", "Review deleted")
                        onRatingReset() // update ui
                    }
                    .addOnFailureListener { Log.e("Firestore", "Error deleting review: ${it.message}") }
            } else {
                onRatingReset() // no review to delete but still update ui
            }
        }.addOnFailureListener {
            Log.e("Firestore", "Error fetching review: ${it.message}")
        }
    }


    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
        ,
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                ,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "",
                            fontSize = 18.sp,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.back),
                            contentDescription = "Back",
                            tint = Primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(Background)
            )

        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .background(Background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w500/$posterPath",
                contentDescription = null,
                modifier = Modifier
                    .height(315.dp)
                    .width(210.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))



            Rating(5, selectedStars = userRating?:0, showId = serieId,
                onRatingChange = { newRating ->
                    userRating = newRating
                },
                onRatingConfirm = {onRatingConfirmed()})

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                WatchButton(
                    watchState = watchState,
                    onWatchStateChange = { watchState = it },
                    onRatingReset = { resetRating(db, userId, serieId) { userRating = null } },
                    userId = userId,
                    serieId = serieId,
                    posterPath = posterPath
                )



                FavoriteButton(showId = serieId, userId = FirebaseAuth.getInstance().currentUser?.uid ?: "", posterPath = posterPath)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = serieName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Tertiary,
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 10.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(5.dp))

            HorizontalDivider(
                color = Secondary,
                thickness = 2.dp,
                modifier = Modifier.padding(horizontal = 30.dp)
            )

            Informations(airDate, voteAverage = voteAvg)

            HorizontalDivider(
                color = Secondary,
                thickness = 2.dp,
                modifier = Modifier.padding(horizontal = 30.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Synopsis",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                textAlign = TextAlign.Left,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
            )

            Text(
                text = serieOverview,
                fontSize = 16.sp,
                color = Tertiary,
                modifier = Modifier.padding(horizontal = 30.dp),
                textAlign = TextAlign.Justify
            )

            Spacer(modifier = Modifier.height(10.dp))

        }
    }
}

@Composable
fun Rating(
    maxStars: Int = 5,
    selectedStars: Int,
    showId: String,
    onRatingChange: (Int) -> Unit,
    onRatingConfirm: () -> Unit
) {

    val db = FirebaseFirestore.getInstance()
    val userDoc = db.collection("users").document(FirebaseAuth.getInstance().currentUser?.uid ?: "")

    var showDialog by remember { mutableStateOf(false) }
    var tempRating by remember { mutableStateOf(selectedStars) }

    Row(
        horizontalArrangement = Arrangement.spacedBy((-3).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..maxStars) {
            IconButton(onClick = {
                tempRating = i
                showDialog = true
            }) {
                Icon(
                    painter = painterResource(
                        id = if (i <= selectedStars) R.drawable.filledstar else R.drawable.emptystar
                    ),
                    contentDescription = "Star $i",
                    tint = Yellow
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirm Rating") },
            text = { Text("Are you sure you want to give this show a rating of $tempRating stars?") },
            confirmButton = {
                TextButton(onClick = {
                    SaveReview(db, userDoc.id, showId = showId, tempRating)
                    onRatingChange(tempRating)
                    onRatingConfirm()
                    showDialog = false
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                }) {
                    Text("No")
                }
            }
        )
    }
}

fun SaveReview(db: FirebaseFirestore, userId: String, showId: String, rating: Int) {
    val reviewRef = db.collection("reviews")
        .whereEqualTo("userId", userId)
        .whereEqualTo("showId", showId)

    reviewRef.get().addOnSuccessListener { documents ->
        if (!documents.isEmpty) {
            // if review exists -> update
            val doc = documents.documents.first()
            val oldRating = doc.getLong("rating")?.toInt()
            val docId = doc.id
            db.collection("reviews").document(docId)
                .update("rating", rating, "timestamp", FieldValue.serverTimestamp())
                .addOnSuccessListener {
                    Log.d("Firestore", "Review updated")
                    updateUserAverageRating(db, userId, rating, oldRating)
                }
                .addOnFailureListener {
                    Log.e("Firestore", "Error updating review: ${it.message}")
                }
        } else {
            // new review
            val newReview = hashMapOf(
                "userId" to userId,
                "showId" to showId,
                "rating" to rating,
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("reviews").add(newReview)
                .addOnSuccessListener {
                    Log.d("Firestore", "Review added")
                    // old rating is null because it's a new review
                    updateUserAverageRating(db, userId, rating, null)
                }
                .addOnFailureListener {
                    Log.e("Firestore", "Error adding review: ${it.message}")
                }
        }
    }.addOnFailureListener {
        Log.e("Firestore", "Error fetching review: ${it.message}")
    }
}


fun updateUserAverageRating(
    db: FirebaseFirestore,
    userId: String,
    newRating: Int,
    oldRating: Int?
) {
    val userDoc = db.collection("users").document(userId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(userDoc)
        // 0 if no ratings yet
        val totalRating = snapshot.getLong("totalRating") ?: 0L
        val ratingCount = snapshot.getLong("ratingCount") ?: 0L

        // if rating already exists, substract old rating and add new rating
        // otherwise, just add new rating and increment count
        val newTotalRating: Long
        val newRatingCount: Long

        if (oldRating != null) {
            newTotalRating = totalRating - oldRating + newRating
            newRatingCount = ratingCount // no increment on update
        } else {
            newTotalRating = totalRating + newRating
            newRatingCount = ratingCount + 1
        }

        // calculate new avg
        val newAverage = if (newRatingCount > 0) newTotalRating.toDouble() / newRatingCount else 0.0

        // update user document
        transaction.update(userDoc, mapOf(
            "totalRating" to newTotalRating,
            "ratingCount" to newRatingCount,
            "averageRating" to newAverage
        ))
    }.addOnSuccessListener {
        Log.d("Firestore", "User average rating updated successfully.")
    }.addOnFailureListener {
        Log.e("Firestore", "Error updating user average rating: ${it.message}")
    }
}


fun deleteRatingAndUpdateAverage(
    db: FirebaseFirestore,
    userId: String,
    rating: Int  // the rating value that is being deleted
) {
    val userDoc = db.collection("users").document(userId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(userDoc)
        val totalRating = snapshot.getLong("totalRating") ?: 0L
        val ratingCount = snapshot.getLong("ratingCount") ?: 0L

        // if no ratings, all values = 0
        if (ratingCount <= 0) {
            transaction.update(userDoc, mapOf(
                "totalRating" to 0L,
                "ratingCount" to 0L,
                "averageRating" to 0.0
            ))
            return@runTransaction
        }

        val newTotalRating = totalRating - rating
        val newRatingCount = ratingCount - 1
        val newAverage = if (newRatingCount > 0) newTotalRating.toDouble() / newRatingCount else 0.0

        transaction.update(userDoc, mapOf(
            "totalRating" to newTotalRating,
            "ratingCount" to newRatingCount,
            "averageRating" to newAverage
        ))
    }
        .addOnSuccessListener {
            Log.d("Firestore", "User average updated successfully after deletion.")
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Error updating average after deletion: ${e.message}")
        }
}


fun fetchUserRating(db: FirebaseFirestore, userId: String, showId: String, onResult: (Int?) -> Unit) {
    db.collection("reviews")
        .whereEqualTo("userId", userId)
        .whereEqualTo("showId", showId)
        .get()
        .addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                val rating = documents.documents.first().getLong("rating")?.toInt()
                onResult(rating)
            } else {
                onResult(null) // Pas encore de note
            }
        }
        .addOnFailureListener {
            Log.e("Firestore", "Error fetching rating", it)
            onResult(null)
        }
}



@Composable
fun WatchButton(
    watchState: WatchState,
    onWatchStateChange: (WatchState) -> Unit,
    onRatingReset: () -> Unit,
    userId: String,
    serieId: String,
    posterPath: String?
) {
    val db = FirebaseFirestore.getInstance()
    val userDoc = db.collection("users").document(userId)
    val serieDoc = userDoc.collection("showsStatus").document(serieId)

    OutlinedButton(
        modifier = Modifier
            .width(150.dp)
            .padding(5.dp),
        onClick = {
            val newState = when (watchState) {
                WatchState.WatchNow -> WatchState.Watching
                WatchState.Watching -> WatchState.Watched
                WatchState.Watched -> WatchState.WatchNow
            }
            onWatchStateChange(newState)

            if (newState == WatchState.WatchNow) {
                onRatingReset()
                serieDoc.delete() // supprime le document si etat = watch now
                    .addOnSuccessListener { Log.d("Firestore", "Show removed from watchlist") }
                    .addOnFailureListener { Log.e("Firestore", "Error removing show: ${it.message}") }
                return@OutlinedButton
            }

            serieDoc.get().addOnSuccessListener { document ->
                val existingPosterPath = document.getString("posterPath") ?: posterPath

                val updateData = mapOf(
                    "watchState" to newState.name,
                    "posterPath" to existingPosterPath
                )

                serieDoc.set(updateData)
                    .addOnSuccessListener { Log.d("Firestore", "Watch state updated with posterPath") }
                    .addOnFailureListener { Log.e("Firestore", "Error updating watch state: ${it.message}") }
            }
        },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when (watchState) {
                WatchState.WatchNow, WatchState.Watched -> Color.Transparent
                WatchState.Watching -> Primary
            },
            contentColor = when (watchState) {
                WatchState.WatchNow -> Primary
                WatchState.Watching -> Background
                WatchState.Watched -> Green
            }
        ),
        elevation = ButtonDefaults.buttonElevation(5.dp),
        border = when (watchState) {
            WatchState.WatchNow -> BorderStroke(2.dp, Primary)
            WatchState.Watched -> BorderStroke(2.dp, Green)
            WatchState.Watching -> BorderStroke(2.dp, Primary)
        }
    ) {
        Text(
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            text = when (watchState) {
                WatchState.Watching -> "Watching"
                WatchState.Watched -> "Watched"
                else -> "Watch Now"
            }
        )
    }
}





@Composable
fun FavoriteButton(showId: String, posterPath: String?, userId: String) {
    val db = FirebaseFirestore.getInstance()
    val userDoc = db.collection("users").document(userId)

    var favorite by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        userDoc.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val favorites = document.get("favorites") as? List<HashMap<String, String>> ?: emptyList()
                favorite = favorites.any { it["showId"] == showId }
            }
        }
    }

    IconButton(onClick = {
        favorite = !favorite
        val favoriteData = hashMapOf("showId" to showId, "posterPath" to posterPath)

        if (favorite) {
            userDoc.update("favorites", FieldValue.arrayUnion(favoriteData))
        } else {
            userDoc.update("favorites", FieldValue.arrayRemove(favoriteData))
        }
    }) {
        Icon(
            painter = painterResource(
                id = if (favorite) R.drawable.filledheart else R.drawable.emptyheart
            ),
            contentDescription = "Favorite",
            tint = Red
        )
    }
}



@Composable
fun Informations(airDate: String?, voteAverage : String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 5.dp)
        ) {

            Text(
                text = "AIR DATE",
                fontSize = 16.sp,
                color = Primary,
                textAlign = TextAlign.Left,
                modifier = Modifier.weight(1f)
            )

            if (airDate != null) {
                Text(
                    text = airDate.trim().substring(0, 4),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Right,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 5.dp)
        ) {

            Text(
                text = "RATING",
                fontSize = 16.sp,
                color = Primary,
                textAlign = TextAlign.Left,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = voteAverage + " out of 10",
                textAlign = TextAlign.Right,
                modifier = Modifier.weight(1f)

            )

        }
    }
}
