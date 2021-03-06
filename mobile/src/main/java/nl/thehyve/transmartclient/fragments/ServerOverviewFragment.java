package nl.thehyve.transmartclient.fragments;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;

import nl.thehyve.transmartclient.R;
import nl.thehyve.transmartclient.apiItems.Study;
import nl.thehyve.transmartclient.rest.RestInteractionListener;
import nl.thehyve.transmartclient.rest.ServerResult;
import nl.thehyve.transmartclient.rest.TransmartServer;

/**
 * Created by Ward Weistra on 01-12-14.
 * Copyright (c) 2015 The Hyve B.V.
 * This code is licensed under the GNU Lesser General Public License,
 * version 3, or (at your option) any later version.
 */

public class ServerOverviewFragment extends Fragment {
    private static final String ARG_TRANSMARTSERVER = "transmartServer";
    private static final String TAG = "ServerOverviewFragment";
    private OnFragmentInteractionListener mListener;
    private RestInteractionListener restInteractionListener;
    private MenuListener menuListener;

    private TransmartServer transmartServer;
    private ArrayList<Study> studyList;

    private RecyclerView.Adapter mAdapter;

    Gson gson = new Gson();

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment ServerOverviewFragment.
     */

    public static ServerOverviewFragment newInstance(TransmartServer transmartServer) {
        ServerOverviewFragment fragment = new ServerOverviewFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_TRANSMARTSERVER, transmartServer);
        fragment.setArguments(args);
        return fragment;
    }

    public ServerOverviewFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);

        studyList = new ArrayList<>();
        mAdapter = new ServerListAdapter(studyList);

        if (getArguments() != null) {
            transmartServer = getArguments().getParcelable(ARG_TRANSMARTSERVER);

            assert transmartServer != null;
            TransmartServer.ConnectionStatus connectionStatus = transmartServer.getConnectionStatus();
            if (connectionStatus == TransmartServer.ConnectionStatus.CONNECTED) {
                new StudiesGetter().execute();
            } else if (connectionStatus == TransmartServer.ConnectionStatus.ACCESSTOKENEXPIRED
                    || connectionStatus == TransmartServer.ConnectionStatus.USEREFRESHTOKEN) {
                restInteractionListener.authorizationLost(transmartServer);
            } else if (connectionStatus == TransmartServer.ConnectionStatus.REFRESHTOKENEXPIRED) {
                restInteractionListener.reconnectDialog(transmartServer);
            } else {
                restInteractionListener.notConnectedYet(transmartServer);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_serveroverview, container, false);

        // Set the adapter
        RecyclerView mRecyclerView = (RecyclerView) rootView.findViewById(R.id.studyList);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this.getContext());
        mRecyclerView.setLayoutManager(mLayoutManager);

//      TODO Show waiting sign: "Retrieving studies"

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.server_overview_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                Log.d(TAG, "Reconnect server");
                new StudiesGetter().execute();
                return true;
            case R.id.action_removeServer:
                Log.d(TAG, "Remove server");
                mListener.removeServerDialog(transmartServer);
                return true;
            case R.id.action_reconnectServerDialog:
                Log.d(TAG, "Reconnect server via dialog");
                restInteractionListener.reconnectDialog(transmartServer);
                return true;
            default:
                break;
        }

        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(transmartServer.getServerLabel());
        menuListener.setMenuItemChecked(transmartServer.getMenuItemID());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnFragmentInteractionListener) context;
            restInteractionListener = (RestInteractionListener) context;
            menuListener = (MenuListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnFragmentInteractionListener" +
                    ", RestInteractionListener" +
                    " and MenuListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        restInteractionListener = null;
        menuListener = null;
    }

    public class ServerListAdapter extends RecyclerView.Adapter<ServerListAdapter.ViewHolder> {

        private ArrayList<Study> studyList;

        public ServerListAdapter(ArrayList<Study> studyList) {
            this.studyList = studyList;
        }

        @Override
        public ServerListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.study_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ServerListAdapter.ViewHolder holder, int position) {
            holder.textStudyName.setText(studyList.get(position).getOntologyTerm().getName());
            holder.textStudyId.setText(String.format("(%s)", studyList.get(position).getId()));
        }

        @Override
        public int getItemCount() {
            return studyList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            public TextView textStudyName;
            public TextView textStudyId;

            public ViewHolder(View itemView) {
                super(itemView);
                itemView.setClickable(true);
                itemView.setOnClickListener(this);
                textStudyName = (TextView) itemView.findViewById(R.id.studyName);
                textStudyId   = (TextView) itemView.findViewById(R.id.studyId);
            }

            @Override
            public void onClick(View v) {
                Log.d(TAG, "Clicked position " + getAdapterPosition() + ": " + studyList.get(getAdapterPosition()).getId());
                mListener.onStudyClicked(studyList.get(getAdapterPosition()).getId(), transmartServer);
            }
        }

    }

    public interface OnFragmentInteractionListener {
        void onStudyClicked(String studyId, TransmartServer transmartServer);
        void removeServerDialog(TransmartServer transmartServer);
    }

    private class StudiesGetter extends AsyncTask<Void, Void, ServerResult> {

        @Override
        protected ServerResult doInBackground(Void... params) {

            ServerResult serverResult = new ServerResult();

            String serverUrl    = transmartServer.getServerUrl();
            String access_token = transmartServer.getAccess_token();

            String query = serverUrl + "/"
                    + "studies"
                    ;

            Log.v(TAG, "Sending query: [" + query + "].");

            return serverResult.getServerResult(access_token, query);
        }

        @Override
        protected void onPostExecute(ServerResult serverResult) {
            super.onPostExecute(serverResult);

            if (serverResult.getResponseCode() == 200) {
                JsonParser parser = new JsonParser();
                JsonArray jArray;
                try {
                    // Return for newer APIs should be a JsonObject with a studies JsonArray in it
                    JsonObject json = (JsonObject) parser.parse(serverResult.getResult());
                    jArray = json.get("studies").getAsJsonArray();
                } catch (java.lang.ClassCastException e1){
                    try {
                        // Return for older APIs should be a JsonArray directly
                        jArray = (JsonArray) parser.parse(serverResult.getResult());
                    } catch (java.lang.ClassCastException e2){
                        throw new ClassCastException("Studies call doesn't return JsonObject or JsonArray");
                    }
                }

                Log.i(TAG, jArray.toString());

                studyList.clear();

                for (int i = 0; i < jArray.size(); i++) {
                    JsonElement studyJSON = jArray.get(i);
                    Study study = gson.fromJson(studyJSON, Study.class);
                    String studyId = study.getId();
                    String studyName = study.getOntologyTerm().getName();
                    Log.i(TAG, "Study: " + studyName + " (" + studyId + ")");
                    studyList.add(study);
                }

                mAdapter.notifyDataSetChanged();
            } else if (serverResult.getResponseCode() == 401) {
                if (restInteractionListener != null) {
                    restInteractionListener.authorizationLost(transmartServer);
                }
            } else if (serverResult.getResponseCode() == 0) {
                if (restInteractionListener != null) {
                    restInteractionListener.connectionLost(transmartServer);
                }
            } else {
                Toast toast = Toast.makeText(getActivity().getApplicationContext(), "Server responded with code "
                        + serverResult.getResponseCode() + ": "
                        + serverResult.getResponseDescription(), Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }
}