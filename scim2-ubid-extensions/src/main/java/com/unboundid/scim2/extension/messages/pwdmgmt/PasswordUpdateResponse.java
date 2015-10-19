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

package com.unboundid.scim2.extension.messages.pwdmgmt;

import com.unboundid.scim2.common.BaseScimResource;
import com.unboundid.scim2.common.annotations.Schema;
import com.unboundid.scim2.common.annotations.Attribute;
import com.unboundid.scim2.common.types.AttributeDefinition;

/**
 * The response for password change requests.
 */
@Schema(description = "Information about password change requests",
        id = "urn:unboundid:scim:api:messages:2.0:PasswordUpdateResponse",
        name = "PasswordUpdateResponse")
public class PasswordUpdateResponse extends BaseScimResource
{

  @Attribute(
      description = "Contains a generated password" +
          " if a password was generated.",
      mutability = AttributeDefinition.Mutability.READ_ONLY)
  private String generatedPassword;

  /**
   * Gets the server generated password.
   *
   * @return the generated password.
   */
  public String getGeneratedPassword()
  {
    return generatedPassword;
  }

  /**
   * Sets the server generated password.
   *
   * @param generatedPassword the generated password.
   */
  public void setGeneratedPassword(final String generatedPassword)
  {
    this.generatedPassword = generatedPassword;
  }
}