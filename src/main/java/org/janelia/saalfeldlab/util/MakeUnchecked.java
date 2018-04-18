package org.janelia.saalfeldlab.util;

import java.util.function.Function;
import java.util.function.Supplier;

public class MakeUnchecked
{

	public static interface CheckedFunction< T, U >
	{
		public U apply( T t ) throws Exception;
	}

	public static interface CheckedSupplier< T >
	{
		public T get() throws Exception;
	}

	public static < T, U > Function< T, U > unchecked( final CheckedFunction< T, U > func )
	{
		return t -> {
			try
			{
				return func.apply( t );
			}
			catch ( final Exception e )
			{
				if ( e instanceof RuntimeException )
					throw ( RuntimeException ) e;
				throw new RuntimeException( e );
			}
		};
	}

	public static < T > Supplier< T > unchecked( final CheckedSupplier< T > supplier )
	{
		return () -> {
			try
			{
				return supplier.get();
			}
			catch ( final Exception e )
			{
				if ( e instanceof RuntimeException )
					throw ( RuntimeException ) e;
				throw new RuntimeException( e );
			}
		};
	}

}