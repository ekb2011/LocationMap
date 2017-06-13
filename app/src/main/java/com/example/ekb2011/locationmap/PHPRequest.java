package com.example.ekb2011.locationmap;

import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ekb2011 on 2017-03-31.
 */

public class PHPRequest extends StringRequest {
    final static private String URL="http://ekb2011.cafe24.com/Data_insert.php";
    private Map<String, String> parameters;
    public PHPRequest(String userX, String userY, String userNum, Response.Listener<String> listener){
        super(Method.POST, URL, listener, null);
        parameters=new HashMap<>();
        parameters.put("userX", userX);
        parameters.put("userY", userY);
        parameters.put("userNum", userNum);
    }
    @Override
    public Map<String, String> getParams(){
        return parameters;
    }

}


