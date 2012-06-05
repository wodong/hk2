/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.hk2.inhabitants;

import org.glassfish.hk2.api.Metadata;
import org.glassfish.hk2.classmodel.reflect.*;
import org.jvnet.hk2.annotations.Contract;
import org.jvnet.hk2.annotations.ContractProvided;
import org.jvnet.hk2.annotations.FactoryFor;
import org.jvnet.hk2.annotations.InhabitantAnnotation;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.MultiMap;
import sun.misc.VM;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Inhabitant scanner based on introspection information rather than statically
 * generated inhabitant file
 */
public class InhabitantIntrospectionScanner implements Iterable<InhabitantParser> {

    final Types types;
  
    final Iterator<AnnotatedElement> inhabitantAnnotations;
    Iterator<AnnotatedElement> current;

    @SuppressWarnings("unchecked")
    public InhabitantIntrospectionScanner(ParsingContext context) {
        types = context.getTypes();
        AnnotationType am = types.getBy(AnnotationType.class, InhabitantAnnotation.class.getName());
        if (am==null) {                                  
            inhabitantAnnotations = Collections.EMPTY_LIST.iterator();
        } else {
            Collection<AnnotatedElement> ccc = am.allAnnotatedTypes();
            inhabitantAnnotations = ccc.iterator();
        }
        fetch();
    }

    /*
     * puts current in the first non empty iterator.
     */
    @SuppressWarnings("unchecked")
    private void fetch() {
        if (!inhabitantAnnotations.hasNext()) {
            current = Collections.EMPTY_LIST.iterator();
            return;
        }
        do {
            AnnotationType am = AnnotationType.class.cast(inhabitantAnnotations.next());
            current = am.allAnnotatedTypes().iterator();
        } while (!current.hasNext() && inhabitantAnnotations.hasNext());
    }

    public boolean isContract(AnnotatedElement type) {
        // must be annotated with @Contract
        if (null == type) return false;
        return type.getAnnotation(Contract.class.getName())!=null;
    }


    public void findInterfaceContracts(InterfaceModel im, Set<String> interfaces, Set<String> annInterfaces) {
        if (im.getParent()!=null) {
            findInterfaceContracts(im.getParent(), interfaces, annInterfaces);
        }
        if (isContract(im)) {
            if (im instanceof AnnotationType) {
              String name = im.getName();
              if (!name.equals(FactoryFor.class.getName())) {
                annInterfaces.add(name);
              }
            } else {
              interfaces.add(im.getName());
            }
        }
        findContractsFromAnnotations(im, interfaces, annInterfaces);
    }

    public void findContractsFromAnnotations(AnnotatedElement ae, Set<String> interfaces, Set<String> annInterfaces) {
        for (AnnotationModel am : ae.getAnnotations()) {
            AnnotationType at = am.getType();
            findInterfaceContracts(at, interfaces, annInterfaces);
            
            String name = at.getName();
            if (name.equals(ContractProvided.class.getName())) {
              String val = scrub(am.getValues().get("value"));
              if (null != val) {
                interfaces.add(val);
              }
            } else if (name.equals(FactoryFor.class.getName())) {
              Object rawObj = am.getValues().get("value");
              if (Collection.class.isInstance(rawObj)) {
                Collection<?> coll = (Collection<?>) am.getValues().get("value");
                for (Object obj : coll) {
                  String val = scrub(obj);
                  if (null != val) {
                    annInterfaces.add(name + ":" + val);
                  }
                }
              } else {
                String val = scrub(rawObj);
                if (null != val) {
                  annInterfaces.add(name + ":" + val);
                }
              }
            }
        }
    }
    
    public static String scrub(Object obj) {
        if (null == obj) {
            return null;
        }
        if (obj instanceof String) {
          return (String)obj;
        }
        String mangled = obj.toString();
        if (mangled.startsWith("L") && mangled.endsWith(";")) {
            mangled = mangled.substring(1, mangled.length()-1);
        }
        return mangled.replace("/", ".");
    }

    public void findContracts(ClassModel cm, Set<String> contracts, Set<String> annotationTypeInterfaces) {

        // It is questionable that the indexes will contain the fully qualified
        // parameterized type as well as the raw type but it's safer for
        // backward compatibility.
        for (ParameterizedInterfaceModel pim : cm.getParameterizedInterfaces()) {
            if (isContract(pim.getRawInterface())) {
                contracts.add(pim.getName());
            }
        }

        // find abstract classes annotated with @Contract
        ClassModel currentClassModel = cm.getParent();
        while(currentClassModel!=null) {
            if (isContract(currentClassModel)) {
                contracts.add(currentClassModel.getName());
            }
            currentClassModel = currentClassModel.getParent();
        }

        for (InterfaceModel im : cm.getInterfaces()) {
            getAllContractInterfaces(im, contracts);
        }

        findContractsFromAnnotations(cm, contracts, annotationTypeInterfaces);

        // walk parent chain too
        ClassModel parent = cm.getParent();
        if (null == parent) {
          // at this point, we check all interfaces to see if they have super interfaces
          Set<String> newIfaces = new LinkedHashSet<String>();
          for (String ifaceName : contracts) {
            InterfaceModel iface = types.getBy(InterfaceModel.class, ifaceName);
            getAllContractInterfaces(iface, newIfaces);
          }
          contracts.addAll(newIfaces);
        } else if (!parent.getName().equals(Object.class.getName())) {
          findContracts(parent, contracts, annotationTypeInterfaces);
        }
    }

    /**
     * Retrieves the "extra" meta data from drilling into each annotation's methods.
     * 
     * @param dest
     * @param ae
     */
    public static void populateExtraInhabitantMetaData(MultiMap<String, String> dest, AnnotatedElement ae) {
      for (AnnotationModel model : ae.getAnnotations()) {
        for (MethodModel mm : model.getType().getMethods()) {
          populateExtraInhabitantMetaData(dest, model, mm);
        }
        
        for (AnnotationModel subModel : model.getType().getAnnotations()) {
          for (MethodModel mm : subModel.getType().getMethods()) {
            populateExtraInhabitantMetaData(dest, subModel, mm);
          }
        }
      }
    }

    private static void populateExtraInhabitantMetaData(MultiMap<String, String> dest, AnnotationModel model, MethodModel mm) {
      if (null != mm) {
        AnnotationModel ma = mm.getAnnotation(Metadata.class.getName());
        if (null != ma) {
          Object tag = ma.getValues().get("value");
          Object val = model.getValues().get(mm.getName());
          if (null != tag) {
            String tagStr = tag.toString();
            if (null != val) {
              add(dest, tagStr, val.toString());
            } else {
              tag = mm.getName();
              val = ((AnnotationType)mm.getDeclaringType()).getDefaultValues().get(tag);
              if (null != val) {
                add(dest, tagStr, val.toString());
              }
            }
          }
        }
      }
    }

    private static void add(MultiMap<String, String> dest, String key, String val) {
      List<String> vals = dest.get(key);
      if (null == vals || !vals.contains(val)) {
        dest.add(key, val);
      }
    }

    /**
     * add all @Contract annotated interfaces extended by the passed interface model
     * to the provided list.
     *
     * @param im interface model to get all @Contract annotated extended interfaces from
     * @param interfaces list of interfaces im is extending
     */
    private void getAllContractInterfaces(InterfaceModel im, Collection<String> interfaces) {
        if (im==null) {
          return;
        }
        
        if (isContract(im)) {
            interfaces.add(im.getName());
        }
        for (InterfaceModel implementedIntf : im.getInterfaces()) {
            getAllContractInterfaces(implementedIntf, interfaces);
        }
    }

    @Override
    public Iterator<InhabitantParser> iterator() {
        return new Iterator<InhabitantParser>() {
            public boolean hasNext() {
                return current.hasNext();
            }

            public InhabitantParser next() {
                final AnnotatedElement ae = current.next();
                InhabitantParser ip = new InhabitantParser() {
                    @Override
                    public Iterable<String> getIndexes() {
                        if (ae instanceof ClassModel) {
                            final ClassModel cm = (ClassModel) ae;

                            final LinkedHashSet<String> implInterfaces = new LinkedHashSet<String>();
                            final LinkedHashSet<String> implAnnotationInterfaces = new LinkedHashSet<String>();
                            findContracts(cm, implInterfaces, implAnnotationInterfaces);
                            
                            final Iterator<String> interfaces = implInterfaces.iterator();
                            final Iterator<String> annInterfaces = implAnnotationInterfaces.iterator();
                            return new Iterable<String>() {
                                public Iterator<String> iterator() {
                                    return new Iterator<String>() {
                                        public boolean hasNext() {
                                            return interfaces.hasNext() || annInterfaces.hasNext();
                                        }

                                        public String next() {
                                            if (interfaces.hasNext()) {
                                              final AnnotationModel am = cm.getAnnotation(Service.class.getName());
                                              String contract = interfaces.next();
                                              String name = (String) am.getValues().get("name");
                                              if (name==null || name.isEmpty()) {
                                                  return contract;
                                              } else {
                                                  return contract+":"+name;
                                              }
                                            } else {
                                              String contract = annInterfaces.next();
                                              return contract;
                                            }
                                        }

                                        public void remove() {
                                            throw new UnsupportedOperationException();
                                        }
                                    };
                                }
                            };

                        }
                        // this should be an error...
                        return (new ArrayList<String>());
                    }

                    @Override
                    public String getImplName() {
                        return ae.getName();
                    }

                    @Override
                    public void setImplName(String name) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String getLine() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void rewind() {
                      // NOP
                    }

                    @Override
                    public MultiMap<String, String> getMetaData() {
                        MultiMap<String, String> mm = new MultiMap<String, String>();
                        final AnnotationModel am = ae.getAnnotation(Service.class.getName());
                        if (null != am) {
                          Object metaObj = am.getValues().get("metadata");
                          if (null != metaObj) {
                            String meta = metaObj.toString();
                            String [] split = meta.split(",");
                            for (String entry : split) {
                              String [] split2 = entry.split("=");
                              if (2 == split2.length) {
                                mm.add(split2[0], split2[1]);
                              }
                            }
                          }
                        }

                        // add all qualifiers.
                        for (AnnotationModel annotationModel : ae.getAnnotations()) {
                            if (annotationModel.getType().getAnnotation("javax.inject.Qualifier")!=null) {
                                mm.add(InhabitantsFile.QUALIFIER_KEY, annotationModel.getType().getName());
                            }
                        }

                        
                        // append to this all metadata recovered from InhabitantMetadata annotation
                        populateExtraInhabitantMetaData(mm, ae);
                        
                        return mm;
                    }
                };
                if (!current.hasNext()) {
                    fetch();
                }
                return ip;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
