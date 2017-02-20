package ca.stefanm.webtodo

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import android.widget.Toast
import ca.stefanm.webtodo.eventbus.TodoListUpdatedEvent
import ca.stefanm.webtodo.localstorage.Session
import ca.stefanm.webtodo.models.TodoItem
import ca.stefanm.webtodo.models.TodoList
import ca.stefanm.webtodo.webservice.LoginWebServiceClient
import ca.stefanm.webtodo.webservice.TodoListWebServiceClient
import hugo.weaving.DebugLog
import org.greenrobot.eventbus.EventBus
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

/**
 * Created by Stefan on 2/11/2017.
 */
object StorageController {

    val TAG = "StorageController"



    data class StorageControllerResult<T>(
            val data : T?, /* Populated on the Get methods */
            val networkCall : Boolean = false, /* Is the data from local storage or from network? */
            val success : Boolean = false /* Did we successfully get data? */
    )

    //CRUD Handler.
    //If no network, then modification not allowed, just read from local.



    open fun hasNetwork(context: Context) : Boolean {

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager?.activeNetworkInfo?.isConnected ?: false
    }


    fun getTodoList(context: Context) : StorageControllerResult<TodoList> {
        if (!hasNetwork(context)){
            return StorageControllerResult<TodoList>(
                    data = Session(context).todoList,
                    networkCall = false,
                    success = true
            )
        } else {
            val client = TodoListWebServiceClient.getClient(context)
            val (response, success) = performBlockingNetworkCall(client.getAllTodoItems())

            if (success && response != null && response.body() != null){
                Session(context).todoList.items = response.body() as ArrayList<TodoItem>
                return StorageControllerResult(
                        data = Session(context).todoList,
                        networkCall = true,
                        success = true
                )
            } else {
                return StorageControllerResult(data = null, networkCall = true, success = false)
            }
        }
    }

    fun getTodoItemById(context: Context, id : Int) : StorageControllerResult<TodoItem>{


        val localitem = Session(context).todoList.items.find { item -> item.id == id }

        if (!hasNetwork(context)){
            return StorageControllerResult(
                    data = localitem,
                    networkCall = false,
                    success = localitem != null
            )
        } else {
            val client = TodoListWebServiceClient.getClient(context)
            val (response, success) = performBlockingNetworkCall(client.getTodoItemById(id))

            if (success && response != null){

                val serverItem = response.body()

                val list = Session(context).todoList.items
                val localIndex = list.indexOfFirst { item -> item.id == id }

                list[localIndex] = serverItem

                //TODO: You may want to do checking in this block for what happens if the server date
                //TODO: is older / newer than the local date. In this app we just do a blind overwrite.

                return StorageControllerResult(data = serverItem, success = true, networkCall = true)

            } else {
                return StorageControllerResult(data = null, networkCall = true, success = false)
            }

        }
    }

//    //TODO finish this for the pulldown!!
//    fun asyncRefreshTodoList(context: Context) : StorageControllerResult<TodoList>{
//
//    }

    //Return whether update to server was successful
    fun updateTodoItemById(context: Context, id: Int, todoItem: TodoItem) : StorageControllerResult<Unit>{

        if (!hasNetwork(context)){
            // Let's not allow the user to modify the list if not connected to the network.
            return StorageControllerResult(data = Unit, networkCall = false, success = false)
        } else {
            //Update the local copy of the item
            var list = Session(context).todoList.items
            val index = list.indexOfFirst { item -> item.id == id }

            if (index == -1){
                return StorageControllerResult(data = Unit, networkCall = false, success = false)
            }

            list[index] = todoItem

            //Update the remote copy

            val client = TodoListWebServiceClient.getClient(context)
            val (response, success) = performBlockingNetworkCall(client.updateTodoItemById(todoItem.id))

            return StorageControllerResult(
                    data = Unit,
                    networkCall = true,
                    success = success && response != null && response.body() != null
            )
        }
    }

    fun deleteTodoItem(context: Context, todoItem: TodoItem) : StorageControllerResult<Unit> {
        if (!hasNetwork(context)){
            // Let's not allow the user to modify the list if not connected to the network.
            return StorageControllerResult(data = Unit, networkCall = false, success = false)
        } else {

            //Delete the local copy.

            var list = Session(context).todoList.items
            var item = list.find { item -> item.id == todoItem.id }

            if (item == null){
                //The item is not stored locally, so we cannot proceed.
                Log.d(TAG, "Item with id ${todoItem.id} not found")
                return StorageControllerResult(data = Unit, networkCall = false, success = false);
            }

            list.remove(item)

            //Delete the remote copy.
            val client = TodoListWebServiceClient.getClient(context)
            val (response, success) = performBlockingNetworkCall(client.deleteTodoItemById(item.id))

            if (success && response != null && response.body() != null){
                Session(context).todoList = response.body()
                return StorageControllerResult(data = Unit, networkCall = true, success = true)
            } else {
                return StorageControllerResult(data = Unit, networkCall = true, success = false)
            }

        }

    }

    fun createTodoItem(context: Context, todoItem: TodoItem) : StorageControllerResult<Unit> {

        if (!hasNetwork(context)){
            return StorageControllerResult(data = Unit, networkCall = false, success = false)
        } else {

            //We have to do the network call first to add the item to the server.
            //The server will return an item with an id number that we can use for future
            //calls.


            val client = TodoListWebServiceClient.getClient(context)
            val (response, success) = performBlockingNetworkCall(client.postNewTodoItemToList(todoItem))

            if (success && response != null && response.body() != null) {
                Session(context).todoList = response.body()
                return StorageControllerResult(data = Unit, networkCall = true, success = true)
            } else {
                return StorageControllerResult(data = Unit, networkCall = true, success = false)
            }
        }
    }


    fun <T> performBlockingNetworkCall(method : Call<T>) : Pair<Response<T>?, Boolean>{
        return TodoListWebServiceClient.performBlockingNetworkCall(TAG, method)
    }

    fun <T> performAsyncNetworkCall (method : Call<T>) {

    }






}