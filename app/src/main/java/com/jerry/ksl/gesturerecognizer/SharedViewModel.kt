package com.jerry.ksl.gesturerecognizer

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.jerry.ksl.gesturerecognizer.model.TrainingImageItem

class SharedViewModel : ViewModel() {
    val selectedTrainingImageItem = MutableLiveData<TrainingImageItem>()
}