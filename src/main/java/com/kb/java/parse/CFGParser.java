package com.kb.java.parse;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.kb.java.dom.expression.Expression;
import com.kb.java.dom.expression.ExpressionAdapter;
import com.kb.java.dom.expression.UnknownExpressionException;
import com.kb.java.dom.expression.Variable;
import com.kb.java.dom.naming.Declaration;
import com.kb.java.dom.naming.MethodSignature;
import com.kb.java.dom.naming.Type;
import com.kb.java.dom.statement.VariableDeclarationStatement;
import com.kb.java.graph.CFGraph;
import com.kb.java.parse.JavaASTParser.ParseType;

public class CFGParser {

	private TypeDecExtractor typeDecExtractor = new TypeDecExtractor();
	private MethodExtractor methodExtractor = new MethodExtractor();
	private ImportsExtractor importExtractor = new ImportsExtractor();

	public List<ClassDeclaration> parse(String source)
			throws UnknownExpressionException {

		JavaASTParser pars = new JavaASTParser(true);
		ASTNode cu = pars.getAST(source, ParseType.COMPILATION_UNIT);
		CompilationUnit unit = (CompilationUnit) cu;
		
		List<TypeDeclaration> types = typeDecExtractor.getTypeDefs(unit);
		Map<String, String> typeMap = importExtractor.getImports(unit);
		ClassVariableResolver resolver = new ClassVariableResolver(typeMap, importExtractor.getPkg());
		ExpressionAdapter eclipseExpressionAdapter = new ExpressionAdapter(resolver, unit);
		List<ClassDeclaration> ret = new LinkedList<ClassDeclaration>();

		for (TypeDeclaration type : types) {
			ClassDeclaration cd = translateTypeDec(unit, type);
			
			for (FieldDeclaration fd : type.getFields()) {
				org.eclipse.jdt.core.dom.Type fdType = fd.getType();
				String typeString = fdType.toString();
				int line = unit.getLineNumber(fdType.getStartPosition());
				int col = unit.getColumnNumber(fdType.getStartPosition());
				int length = fdType.getLength();
				String longType = resolver.getSafeType(typeString);
				Type t = new Type(longType);

				for (Object o : fd.fragments()) {
					VariableDeclarationFragment frag = (VariableDeclarationFragment) o;
					Variable var = new Variable(frag.getName().toString());

					Expression init = eclipseExpressionAdapter.translate(frag
							.getInitializer());
					VariableDeclarationStatement vs = new VariableDeclarationStatement(
							t, var, init);
					resolver.addVarType(var.getName(), longType, line, col, length);
					cd.addFieldDec(vs);
				}
			}
			
			for (MethodDeclaration method : methodExtractor.getMethods(type)) {
				MethodSignature methodSig = translateMethodDec(method);
				CFGraph cfg = new CFGraph(method, resolver, unit);

				methodSig.setCFG(cfg);
				cd.addMethod(methodSig);
			}
			
			Set<TypeLocation> typeLocs = resolver.getTypeLocations();
			cd.setTypeLocations(typeLocs);
			ret.add(cd);
		}

		return ret;
	}

	private MethodSignature translateMethodDec(MethodDeclaration method) {
		String name = method.getName().toString();
		Type retType = null;
		if (method.getReturnType2() != null) {
			retType = new Type(method.getReturnType2().toString());
		}
		List<Declaration> parameterList = new ArrayList<Declaration>();

		for (Object o : method.parameters()) {
			SingleVariableDeclaration dec = (SingleVariableDeclaration) o;
			Variable param = new Variable(dec.getName().toString());
			Type paramType = new Type(dec.getType().toString());
			parameterList.add(new Declaration(paramType, param));
		}

		return new MethodSignature(retType, name, parameterList);
	}

	private ClassDeclaration translateTypeDec(CompilationUnit unit, TypeDeclaration td) {
		Type ty = new Type(unit.getPackage().getName() + "." + td.getName().toString());
		ClassDeclaration cd = new ClassDeclaration(ty);
		return cd;
	}
}
