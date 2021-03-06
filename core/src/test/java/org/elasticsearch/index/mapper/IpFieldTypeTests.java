/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.index.mapper;

import java.net.InetAddress;

import org.apache.lucene.document.InetAddressPoint;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.network.InetAddresses;
import org.elasticsearch.index.mapper.IpFieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;

public class IpFieldTypeTests extends FieldTypeTestCase {
    @Override
    protected MappedFieldType createDefaultFieldType() {
        return new IpFieldMapper.IpFieldType();
    }

    public void testValueFormat() throws Exception {
        MappedFieldType ft = createDefaultFieldType();
        String ip = "2001:db8::2:1";
        BytesRef asBytes = new BytesRef(InetAddressPoint.encode(InetAddress.getByName(ip)));
        assertEquals(ip, ft.docValueFormat(null, null).format(asBytes));

        ip = "192.168.1.7";
        asBytes = new BytesRef(InetAddressPoint.encode(InetAddress.getByName(ip)));
        assertEquals(ip, ft.docValueFormat(null, null).format(asBytes));
    }

    public void testValueForSearch() throws Exception {
        MappedFieldType ft = createDefaultFieldType();
        String ip = "2001:db8::2:1";
        BytesRef asBytes = new BytesRef(InetAddressPoint.encode(InetAddresses.forString(ip)));
        assertEquals(ip, ft.valueForSearch(asBytes));

        ip = "192.168.1.7";
        asBytes = new BytesRef(InetAddressPoint.encode(InetAddresses.forString(ip)));
        assertEquals(ip, ft.valueForSearch(asBytes));
    }

    public void testTermQuery() {
        MappedFieldType ft = createDefaultFieldType();
        ft.setName("field");

        String ip = "2001:db8::2:1";
        assertEquals(InetAddressPoint.newExactQuery("field", InetAddresses.forString(ip)), ft.termQuery(ip, null));

        ip = "192.168.1.7";
        assertEquals(InetAddressPoint.newExactQuery("field", InetAddresses.forString(ip)), ft.termQuery(ip, null));

        ip = "2001:db8::2:1";
        String prefix = ip + "/64";
        assertEquals(InetAddressPoint.newPrefixQuery("field", InetAddresses.forString(ip), 64), ft.termQuery(prefix, null));

        ip = "192.168.1.7";
        prefix = ip + "/16";
        assertEquals(InetAddressPoint.newPrefixQuery("field", InetAddresses.forString(ip), 16), ft.termQuery(prefix, null));

        ft.setIndexOptions(IndexOptions.NONE);
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> ft.termQuery("::1", null));
        assertEquals("Cannot search on field [field] since it is not indexed.", e.getMessage());
    }

    public void testRangeQuery() {
        MappedFieldType ft = createDefaultFieldType();
        ft.setName("field");

        assertEquals(
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("::"),
                        InetAddressPoint.MAX_VALUE),
                ft.rangeQuery(null, null, randomBoolean(), randomBoolean()));

        assertEquals(
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("::"),
                        InetAddresses.forString("192.168.2.0")),
                ft.rangeQuery(null, "192.168.2.0", randomBoolean(), true));

        assertEquals(
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("::"),
                        InetAddresses.forString("192.168.1.255")),
                ft.rangeQuery(null, "192.168.2.0", randomBoolean(), false));

        assertEquals(
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("2001:db8::"),
                        InetAddressPoint.MAX_VALUE),
                ft.rangeQuery("2001:db8::", null, true, randomBoolean()));

        assertEquals(
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("2001:db8::1"),
                        InetAddressPoint.MAX_VALUE),
                ft.rangeQuery("2001:db8::", null, false, randomBoolean()));

        assertEquals(
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("2001:db8::"),
                        InetAddresses.forString("2001:db8::ffff")),
                ft.rangeQuery("2001:db8::", "2001:db8::ffff", true, true));

        assertEquals(
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("2001:db8::1"),
                        InetAddresses.forString("2001:db8::fffe")),
                ft.rangeQuery("2001:db8::", "2001:db8::ffff", false, false));

        assertEquals(
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("2001:db8::2"),
                        InetAddresses.forString("2001:db8::")),
                // same lo/hi values but inclusive=false so this won't match anything
                ft.rangeQuery("2001:db8::1", "2001:db8::1", false, false));

        // Upper bound is the min IP and is not inclusive
        assertEquals(new MatchNoDocsQuery(),
                ft.rangeQuery("::", "::", true, false));

        // Lower bound is the max IP and is not inclusive
        assertEquals(new MatchNoDocsQuery(),
                ft.rangeQuery("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", false, true));

        assertEquals(
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("::"),
                        InetAddresses.forString("::fffe:ffff:ffff")),
                // same lo/hi values but inclusive=false so this won't match anything
                ft.rangeQuery("::", "0.0.0.0", true, false));

        assertEquals(
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("::1:0:0:0"),
                        InetAddressPoint.MAX_VALUE),
                // same lo/hi values but inclusive=false so this won't match anything
                ft.rangeQuery("255.255.255.255", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", false, true));

        assertEquals(
                // lower bound is ipv4, upper bound is ipv6
                InetAddressPoint.newRangeQuery("field",
                        InetAddresses.forString("192.168.1.7"),
                        InetAddresses.forString("2001:db8::")),
                ft.rangeQuery("::ffff:c0a8:107", "2001:db8::", true, true));

        ft.setIndexOptions(IndexOptions.NONE);
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> ft.rangeQuery("::1", "2001::", true, true));
        assertEquals("Cannot search on field [field] since it is not indexed.", e.getMessage());
    }
}
