/*
 * Copyright 2015 UnboundID Corp.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */

package com.unboundid.scim2.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.unboundid.scim2.client.ScimService;
import com.unboundid.scim2.common.Meta;
import com.unboundid.scim2.common.ResourceTypeResource;
import com.unboundid.scim2.common.SchemaResource;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.ServiceProviderConfigResource;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ErrorResponse;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.EnterpriseUserExtension;
import com.unboundid.scim2.common.types.Name;
import com.unboundid.scim2.common.types.PhoneNumber;
import com.unboundid.scim2.common.types.UserResource;
import com.unboundid.scim2.common.utils.SchemaUtils;
import com.unboundid.scim2.server.providers.ScimExceptionMapper;
import com.unboundid.scim2.server.providers.ScimJacksonJsonProvider;
import com.unboundid.scim2.server.providers.RuntimeExceptionMapper;
import com.unboundid.scim2.server.resources.AbstractEndpoint;
import com.unboundid.scim2.server.resources.ResourceTypesEndpoint;
import com.unboundid.scim2.server.resources.SchemasEndpoint;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTestNg;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Collections;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Test for the various endpoints included in the server module.
 */
public class EndpointTestCase extends JerseyTestNg.ContainerPerClassTest
{
  private SchemaResource userSchema;
  private SchemaResource enterpriseSchema;
  private ResourceTypeResource resourceType;
  private ResourceTypeResource singletonResourceType;

  /**
   * {@inheritDoc}
   */
  @Override
  protected Application configure()
  {
    ResourceConfig config = new ResourceConfig();
    config.register(ScimExceptionMapper.class);
    config.register(RuntimeExceptionMapper.class);
    config.register(ScimJacksonJsonProvider.class);
    config.register(ResourceTypesEndpoint.class);
    config.register(SchemasEndpoint.class);

    config.register(TestServiceProviderConfigEndpoint.class);
    config.register(TestResourceEndpoint.class);
    config.register(new TestSingletonResourceEndpoint());

    return config;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void configureClient(final ClientConfig config)
  {
    config.connectorProvider(new ApacheConnectorProvider());
  }

  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    try
    {
      userSchema = SchemaUtils.getSchema(UserResource.class);
      setMeta(SchemasEndpoint.class, userSchema);

      enterpriseSchema = SchemaUtils.getSchema(EnterpriseUserExtension.class);
      setMeta(SchemasEndpoint.class, enterpriseSchema);

      resourceType = new ResourceTypeResource("User", "User Account",
          new URI("/Users"),
          new URI(userSchema.getId()));
      setMeta(ResourceTypesEndpoint.class, resourceType);

      singletonResourceType = new ResourceTypeResource(
          "Singleton User", "Singleton User", "Singleton User Account",
          new URI("/SingletonUsers"), new URI(userSchema.getId()),
          Collections.singletonList(new ResourceTypeResource.SchemaExtension(
              new URI(enterpriseSchema.getId()), true)));
      setMeta(ResourceTypesEndpoint.class, singletonResourceType);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }


  }

  /**
   * Test the service provider config can be retrieved.
   *
   * @throws ScimException if an error occurs.
   */
  @Test
  public void testGetServiceProviderConfig() throws ScimException
  {
    final ServiceProviderConfigResource returnedServiceProviderConfig =
        new ScimService(target()).getServiceProviderConfig();

    assertEquals(returnedServiceProviderConfig,
        TestServiceProviderConfigEndpoint.CONFIG);
  }

  /**
   * Test that modifying the service provider config results in error.
   */
  @Test
  public void testPutServiceProviderConfig()
  {
    try
    {
      ScimService scimService = new ScimService(target());
      scimService.modifyRequest(
          scimService.getServiceProviderConfig()).invoke();
    }
    catch (WebApplicationException e)
    {
      assertEquals(e.getResponse().getStatus(), 501);
      ErrorResponse errorResponse =
          e.getResponse().readEntity(ErrorResponse.class);
      assertEquals(errorResponse.getStatus(), new Integer(501));
    }
    catch (ScimException e)
    {
      e.printStackTrace();
    }
  }

  /**
   * Test that retrieving an invalid endpoint results in 404.
   */
  @Test
  public void testInvalidEndpoint()
  {
    try
    {
      new ScimService(target()).retrieve("badPath", "id", UserResource.class);
      fail();
    }
    catch(ScimException e)
    {
      assertTrue(e instanceof ResourceNotFoundException);
    }
  }

  /**
   * Test all schemas can be retrieved.
   *
   * @throws ScimException if an error occurs.
   */
  @Test
  public void testGetSchemas() throws ScimException
  {
    final ListResponse<SchemaResource> returnedSchemas =
        new ScimService(target()).getSchemas();

    assertEquals(returnedSchemas.getTotalResults(), 2);
    assertTrue(contains(returnedSchemas, userSchema));
    assertTrue(contains(returnedSchemas, enterpriseSchema));
  }

  /**
   * Test an individual schema can be retrieved.
   *
   * @throws ScimException if an error occurs.
   */
  @Test
  public void testGetSchema() throws ScimException
  {
    SchemaResource returnedSchema =
        new ScimService(target()).getSchema(userSchema.getId());

    assertEquals(returnedSchema, userSchema);

    returnedSchema = new ScimService(target()).getSchema(
        enterpriseSchema.getId());

    assertEquals(returnedSchema, enterpriseSchema);
  }

  /**
   * Test all resource types can be retrieved.
   *
   * @throws ScimException if an error occurs.
   */
  @Test
  public void testGetResourceTypes() throws ScimException
  {
    final ListResponse<ResourceTypeResource> returnedResourceTypes =
        new ScimService(target()).getResourceTypes();

    assertEquals(returnedResourceTypes.getTotalResults(), 2);
    assertTrue(contains(returnedResourceTypes, resourceType));
    assertTrue(contains(returnedResourceTypes, singletonResourceType));
  }

  /**
   * Test an individual resource type can be retrieved.
   *
   * @throws ScimException if an error occurs.
   */
  @Test
  public void testGetResourceType() throws ScimException
  {
    final ResourceTypeResource returnedResourceTypeById =
        new ScimService(target()).getResourceType(resourceType.getId());

    assertEquals(returnedResourceTypeById, resourceType);

    final ResourceTypeResource returnedResourceTypeByName =
        new ScimService(target()).getResourceType(
            singletonResourceType.getId());

    assertEquals(returnedResourceTypeByName, singletonResourceType);

    final SchemaResource returnedSchema =
        new ScimService(target()).getSchema(
            returnedResourceTypeById.getSchema().toString());

    assertEquals(returnedSchema, userSchema);


  }

  /**
   * Test an resource endpoint implementation registered as a class.
   *
   * @throws ScimException if an error occurs.
   */
  @Test
  public void testGetUsers() throws ScimException
  {
    Object o = new GenericType<ListResponse<UserResource>>(){};

    final ListResponse<UserResource> returnedUsers =
        new ScimService(target()).searchRequest("Users").
            invoke(UserResource.class);

    assertEquals(returnedUsers.getTotalResults(), 1);
  }

  /**
   * Test create operation.
   *
   * @throws ScimException if an error occurs.
   */
  @Test
  public void testCreate() throws ScimException
  {
    ScimService scimService = new ScimService(target());

    // Create a new user.
    UserResource newUser = new UserResource().setUserName("createUser");
    EnterpriseUserExtension extension =
        new EnterpriseUserExtension().setEmployeeNumber("1234");
    newUser.setExtensionValue(
        SchemaUtils.getSchemaUrn(EnterpriseUserExtension.class), extension);

    UserResource createdUser =
        scimService.create("SingletonUsers", newUser);
    assertNotNull(createdUser.getId());
    assertEquals(createdUser.getUserName(), newUser.getUserName());
    try
    {
      assertEquals(createdUser.getExtensionValue(
          SchemaUtils.getSchemaUrn(EnterpriseUserExtension.class),
          EnterpriseUserExtension.class), extension);
    }
    catch (JsonProcessingException e)
    {
      throw new RuntimeException(e);
    }

    UserResource retrievedUser = scimService.retrieve(createdUser);

    assertEquals(createdUser, retrievedUser);
  }

  /**
   * Test delete operation.
   *
   * @throws ScimException if an error occurs.
   */
  @Test
  public void testDelete() throws ScimException
  {
    ScimService scimService = new ScimService(target());

    // Create a new user.
    UserResource newUser = new UserResource().setUserName("deleteUser");
    UserResource createdUser =
        scimService.create("SingletonUsers", newUser);
    assertNotNull(createdUser.getId());
    assertEquals(createdUser.getUserName(), newUser.getUserName());

    scimService.delete(createdUser);

    try
    {
      scimService.retrieve(createdUser);
      fail("Resource should have been deleted");
    }
    catch(ResourceNotFoundException e)
    {
      // expected
    }
  }

  /**
   * Test put operation.
   *
   * @throws ScimException if an error occurs.
   */
  @Test
  public void testPut() throws ScimException
  {
    ScimService scimService = new ScimService(target());

    // Create a new user.
    UserResource newUser = new UserResource().setUserName("putUser");
    UserResource createdUser =
        scimService.create("SingletonUsers", newUser);

    assertNull(createdUser.getDisplayName());

    createdUser.setDisplayName("Bob");

    UserResource updatedUser = scimService.replace(createdUser);
    assertEquals(updatedUser.getDisplayName(), "Bob");
  }

  /**
   * Test patch operation.
   *
   * @throws ScimException if an error occurs.
   */
  @Test
  public void testPatch() throws ScimException
  {
    ScimService scimService = new ScimService(target());

    // Create a new user.
    UserResource newUser = new UserResource().setUserName("patchUser");
    newUser.setDisplayName("removeMe");
    newUser.setName(new Name().setGivenName("Bob").setFamilyName("Tester"));
    newUser.setEmails(Collections.singletonList(
        new Email().setValue("bob@tester.com").setType("work")));
    UserResource createdUser =
        scimService.create("SingletonUsers", newUser);

    PhoneNumber phone1 = new PhoneNumber().
        setValue("1234567890").setType("home");
    PhoneNumber phone2 = new PhoneNumber().
        setValue("123123123").setType("work").setPrimary(true);

    UserResource updatedUser = scimService.modifyRequest(createdUser).
        removeValues("displayName").
        replaceValue("name.middleName", "the").
        replaceValue("emails[type eq \"work\"].value", "bobNew@tester.com").
        addValues("phoneNumbers", phone1, phone2).invoke();

    assertNull(updatedUser.getDisplayName());
    assertEquals(updatedUser.getName().getMiddleName(), "the");
    assertEquals(updatedUser.getEmails().get(0).getValue(),
        "bobNew@tester.com");
    assertEquals(updatedUser.getPhoneNumbers().size(), 2);
    assertTrue(contains(updatedUser.getPhoneNumbers(), phone1));
    assertTrue(contains(updatedUser.getPhoneNumbers(), phone2));
  }

  private void setMeta(Class<?> resourceClass, ScimResource scimResource)
  {
    ResourceTypeResource resourceType =
        AbstractEndpoint.getResourceType(resourceClass);
    UriBuilder locationBuilder =
        UriBuilder.fromUri(getBaseUri()).path(
            resourceType.getEndpoint().getPath());
    if(scimResource.getId() != null)
    {
      locationBuilder.path(scimResource.getId());
    }

    Meta meta = scimResource.getMeta();
    if(meta == null)
    {
      meta = new Meta();
      scimResource.setMeta(meta);
    }
    meta.setLocation(locationBuilder.build());
    meta.setResourceType(resourceType.getName());
  }

  private <T> boolean contains(Iterable<T> list, T resource)
  {
    for(T r : list)
    {
      if(r.equals(resource))
      {
        return true;
      }
    }
    return false;
  }
}
