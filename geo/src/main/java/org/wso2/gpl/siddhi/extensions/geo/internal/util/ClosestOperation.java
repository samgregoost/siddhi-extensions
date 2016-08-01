/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.gpl.siddhi.extensions.geo.internal.util;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.operation.distance.DistanceOp;
import org.wso2.siddhi.query.api.definition.Attribute;

public class ClosestOperation extends org.wso2.gpl.siddhi.extensions.geo.internal.util.GeoOperation {
    @Override
    public Object operation(Geometry a, Geometry b, Object[] data) {
        DistanceOp distOp = new DistanceOp(a, b);
        return distOp.nearestPoints();
    }

    @Override
    public Object operation(Geometry a, PreparedGeometry b, Object[] data) {
        DistanceOp distOp = new DistanceOp(a, b.getGeometry());
        return distOp.nearestPoints();
    }

    @Override
    public Attribute.Type getReturnType() {
        return null;
    }
}