package org.cf.cloud.servicebroker.memsql.service;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.*;

import org.cf.cloud.servicebroker.memsql.exception.MemSQLServiceException;
import org.cf.cloud.servicebroker.memsql.lib.PasswordGenerator;
import org.cf.cloud.servicebroker.memsql.model.ServiceInstanceBinding;
import org.cf.cloud.servicebroker.memsql.repository.MemSQLServiceInstanceBindingRepository;
import org.cf.cloud.servicebroker.memsql.repository.TestBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceBindingDoesNotExistException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceBindingExistsException;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceAppBindingResponse;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceBindingResponse;
import org.springframework.cloud.servicebroker.model.DeleteServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.service.ServiceInstanceBindingService;
import org.springframework.stereotype.Service;

/**
* MemSQL impl to bind services.  Binding a service does the following:
 * creates a new user in the database - autogenerates the password,
 * saves the ServiceInstanceBinding info to the MemSQL repository
 */
@Service
public class MemSQLServiceInstanceBindingService implements ServiceInstanceBindingService {

	@Autowired
	MemSQLClient memSQLClient;

	@Autowired
	MemSQLAdminService adminService;

	@Autowired
	MemSQLServiceInstanceBindingRepository bindingRepository;

		
//was commented out
	@Autowired
	public MemSQLServiceInstanceBindingService(MemSQLAdminService memsql,
											   MemSQLServiceInstanceBindingRepository bindingRepository) {
		this.adminService = memsql;
		this.bindingRepository = bindingRepository;
	}

	
	@Override
	public CreateServiceInstanceBindingResponse createServiceInstanceBinding(CreateServiceInstanceBindingRequest request)
	throws MemSQLServiceException{

		String bindingId = request.getBindingId();
		System.out.println("Binding ID "+bindingId);
		String serviceInstanceId = request.getServiceInstanceId();
		System.out.println("Service Instance ID = "+serviceInstanceId);

		ServiceInstanceBinding binding = bindingRepository.findOne(bindingId);
		if (binding != null) {
			System.out.print("Nothing found with serviceInstanceId " +serviceInstanceId+ " bindingId "+ bindingId );
			throw new ServiceInstanceBindingExistsException(serviceInstanceId, bindingId);
		}

		String database = serviceInstanceId;
		String username = bindingId;

		/*
			random password generator
		 */
		PasswordGenerator msr = new PasswordGenerator();
		String password = msr.generateRandomString();


		// check if user already exists in the DB

		boolean userExists = adminService.userExists(username);
		if(userExists){
			System.out.println("User already exists. A duplicate user cannot be created");
			throw new MemSQLServiceException("Service already bound with the bindId: " + username);
		}

		Map credentials = new HashMap<String,Object>();
		try {
			DatabaseCredentials dbCreds = adminService.createUser(database, username, password);
					
			credentials.put("uri", dbCreds.getUri());
			credentials.put("jdbcUrl", dbCreds.getJdbcUrl());
			credentials.put("username", dbCreds.getUsername());
			credentials.put("password", dbCreds.getPassword());
			credentials.put("hostname", dbCreds.getHost());
			credentials.put("port", dbCreds.getPort());			
			
			binding = new ServiceInstanceBinding(bindingId, serviceInstanceId, credentials, null, request.getBoundAppGuid());
			bindingRepository.save(binding);

			System.out.println(" binding saved with bindingId " + bindingId + " serviceInstanceId = "+  serviceInstanceId + " BoundAppId = " + request.getBoundAppGuid());

			return new CreateServiceInstanceAppBindingResponse().withCredentials(credentials);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new MemSQLServiceException("Unable to bind to service instance" + e.getMessage());
		}
	}

	@Override
	public void deleteServiceInstanceBinding(DeleteServiceInstanceBindingRequest request) {
		String bindingId = request.getBindingId();
		ServiceInstanceBinding binding = getServiceInstanceBinding(bindingId);

		if (binding == null) {
			throw new ServiceInstanceBindingDoesNotExistException(bindingId);
		}

		adminService.deleteUser(binding.getServiceInstanceId(), bindingId);
		bindingRepository.delete(bindingId);

	}

	public ServiceInstanceBinding getServiceInstanceBinding(String id) {
		return bindingRepository.findOne(id);
	}

}
